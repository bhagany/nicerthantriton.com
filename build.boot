(set-env!
 :source-paths #{"src" "content"}
 :resource-paths #{"resources"}
 :dependencies '[[boot/core "2.6.0" :scope "provided"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [pandeiro/boot-http "0.7.6" :exclusions [org.clojure/clojure]]
                 [adzerk/boot-cljs "1.7.228-2" :scope "test"]
                 [adzerk/boot-reload "0.4.13" :scope "test"]
                 [hiccup "1.0.5" :exclusions [org.clojure/clojure]]
                 [hashobject/boot-s3 "0.1.2-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [perun "0.4.0-SNAPSHOT" :scope "test"]])

(require '[boot.core :as boot]
         '[boot.pod :as pod]
         '[adzerk.boot-cljs :refer [cljs]]
         '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-reload :refer [reload]]
         '[hashobject.boot-s3 :refer [s3-sync]]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[io.perun :as p]
         '[io.perun.core :as perun]
         '[io.perun.meta :as pm]
         '[nicerthantriton.core :as ntt])

(defn slugify-filename
  [filename]
  (->> (str/split filename #"[-\.\s]")
       drop-last
       (str/join "-")
       str/lower-case))

(defn permalinkify
  [{:keys [slug parent-path]}]
  (str "/" parent-path slug ".html"))

(defn tagify
  [entries]
  (->> entries
       (mapcat (fn [entry]
                 (map #(-> [% entry]) (:tags entry))))
       (reduce (fn [result [tag entry]]
                 (let [topic (get ntt/tag-topics tag)
                       path (ntt/topic-href topic)]
                   (-> result
                       (update-in [path :entries] conj entry)
                       (assoc-in [path :group-meta :title] topic))))
               {})))

(defn post?
  [{:keys [has-content parent-path]}]
  (and has-content (= parent-path "posts/")))

(def +recent-posts-defaults+
  {:num-posts 5})

(deftask recent-posts
  "Adds the `n` most recent posts to global metadata as `:recent-posts`"
  [n num-posts NUMPOSTS int "The number of posts to store"]
  (with-pre-wrap fileset
    (let [options (merge +recent-posts-defaults+ *opts*)
          global-meta (pm/get-global-meta fileset)
          recent (->> (pm/get-meta fileset)
                      (filter post?)
                      (sort-by :date-published #(compare %2 %1))
                      (take (:num-posts options)))]
      (perun/report-info "recent-posts" "added %s posts to metadata" (count recent))
      (pm/set-global-meta fileset (assoc global-meta :recent-posts recent)))))

(deftask topics
  "Generates topics from the tags on each post, and adds them to global metadata as `:topics`"
  []
  (with-pre-wrap fileset
    (let [global-meta (pm/get-global-meta fileset)
          topics (->> (pm/get-meta fileset)
                      (mapcat :tags)
                      (map #(get ntt/tag-topics %))
                      set
                      (sort-by #(.indexOf ntt/topic-order %)))]
      (perun/report-info "topics" "added %s generated topics to metadata" (count topics))
      (pm/set-global-meta fileset (assoc global-meta :topics topics)))))

(deftask set-tier
  "Sets the `:tier` key in perun's global metadata, for render fn's to dispatch on"
  [v val VAL kw "The value to set for :tier"]
  (with-pre-wrap fileset
    (let [global-meta (pm/get-global-meta fileset)]
      (perun/report-info "tier" "set :tier to %s" val)
      (pm/set-global-meta fileset (assoc global-meta :tier val)))))

(def minify-css-deps '[[asset-minifier "0.2.0"]])

(deftask minify-css
  "Would you believe that this task finds CSS files, and minifies them?"
  []
  (let [out (boot/tmp-dir!)
        prev (atom nil)
        pod (-> (boot/get-env)
                (update-in [:dependencies] into minify-css-deps)
                pod/make-pod
                future)]
    (boot/with-pre-wrap fileset
      (let [files (->> (boot/fileset-diff @prev fileset :hash)
                       boot/output-files
                       (boot/by-ext [".css"]))]
        (doseq [file files
                :let [new-file (io/file out (boot/tmp-path file))]]
          (io/make-parents new-file)
          (pod/with-call-in @pod
            (asset-minifier.core/minify-css ~(.getPath (boot/tmp-file file))
                                            ~(.getPath new-file))))
        (perun/report-info "minify-css" "Minified %s css file(s)" (count files))
        (-> fileset (boot/add-resource out) boot/commit!)))))

(def content-types
  {"html" "text/html; charset=utf-8"
   "css" "text/css; charset=utf-8"
   "js" "application/javascript; charset=utf-8"
   "svg" "image/svg+xml; charset=utf-8"
   "xml" "application/xml; charset=utf-8"
   "json" "application/json; charset=utf-8"})

(deftask s3-metadata
  "Adds boot-s3-compatible metadata for setting headers on files destined for s3"
  []
  (boot/with-pre-wrap fileset
    (let [output (boot/output-files fileset)
          s3-meta (->> ["html" "css" "js" "svg" "xml" "json"]
                       (map #(->
                              [% (map :path
                                      (boot/by-re
                                       [(re-pattern (str "^public/.*\\." %))]
                                       output))]))
                       (mapcat (fn [[type files]]
                                 (map #(-> [% {:hashobject/boot-s3
                                               {:metadata
                                                {:content-type
                                                 (get content-types type)}}}])
                                      files)))
                       (into {}))]
      (boot/add-meta fileset s3-meta))))

(deftask urls
  "Helper task for encapsulating all url generation stuff"
  [_ filterer FILTERER code "predicate to use for selecting entries (default: `:has-content`)"]
  (let [filterer (or filterer :has-content)]
    (comp (p/slug :slug-fn slugify-filename :filterer filterer)
          (p/permalink :permalink-fn permalinkify :filterer filterer)
          (p/canonical-url :filterer filterer))))

(deftask build
  "Build nicerthantriton.com"
  [t tier TIER kw "The tier we're building for"]
  (comp (p/global-metadata)
        (set-tier :val tier)
        (p/markdown)
        (urls)
        (p/collection :renderer 'nicerthantriton.core/index
                      :filterer post?
                      :meta {:derived true})
        (p/assortment :renderer 'nicerthantriton.core/topic
                      :filterer post?
                      :grouper tagify
                      :meta {:derived true})
        (urls :filterer :derived)
        (recent-posts)
        (topics)
        (p/render :renderer 'nicerthantriton.core/post
                  :filterer post?)
        (p/render :renderer 'nicerthantriton.core/page)
        (p/atom-feed :filterer post?)
        #_(p/print-meta)))

(deftask dev
  "Build nicerthantriton.com dev environment with reloading"
  []
  (comp (serve :resource-root "public/")
        (watch)
        (build :tier :dev)
        (reload :asset-path "/public")
        (cljs)
        #_(p/print-meta)))

(def aws-edn
  (read-string (slurp "aws.edn")))

(deftask deploy
  "Build nicerthantriton.com and deploy to production s3 bucket"
  []
  (comp (build :tier :prod)
        (minify-css)
        (p/inject-scripts :scripts #{"ga-inject.js"})
        (cljs :optimizations :advanced)
        (sift :include [#"\.cljs\.edn$" #"js/main\.out/"] :invert true)
        (s3-metadata)
        (s3-sync :bucket "nicerthantriton.com"
                 :source "public"
                 :access-key (:access-key aws-edn)
                 :secret-key (:secret-key aws-edn)
                 :metadata {:cache-control "max-age=315360000, no-transform, public"})))
