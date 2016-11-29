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
                       (assoc-in [path :group-meta] {:topic topic}))))
               {})))

(def +recent-posts-defaults+
  {:num-posts 5})

(deftask recent-posts
  "Adds the `n` most recent posts to global metadata as `:recent-posts`"
  [n num-posts NUMPOSTS int "The number of posts to store"]
  (with-pre-wrap fileset
    (let [options (merge +recent-posts-defaults+ *opts*)
          global-meta (perun/get-global-meta fileset)
          recent (->> (perun/get-meta fileset)
                      (filter #(= (:parent-path %) "posts/"))
                      (sort-by :date-published #(compare %2 %1))
                      (take (:num-posts options)))]
      (perun/report-info "recent-posts" "Added %s posts to metadata" (count recent))
      (perun/set-global-meta fileset (assoc global-meta :recent-posts recent)))))

(deftask topics
  "Generates topics from the tags on each post, and adds them to global metadata as `:topics`"
  []
  (with-pre-wrap fileset
    (let [global-meta (perun/get-global-meta fileset)
          topics (->> (perun/get-meta fileset)
                      (mapcat :tags)
                      (map #(get ntt/tag-topics %))
                      set
                      (sort-by #(.indexOf ntt/topic-order %)))]
      (perun/report-info "topics" "Added %s generated topics to metadata" (count topics))
      (perun/set-global-meta fileset (assoc global-meta :topics topics)))))

(def minify-css-deps '[[asset-minifier "0.2.0"]])

(deftask minify-css
  []
  (let [out (boot/tmp-dir!)
        prev (atom nil)
        pod (-> (boot/get-env)
                (update-in [:dependencies] into minify-css-deps)
                pod/make-pod
                future)]
    (boot/with-pre-wrap fileset
      (let [files (->> fileset
                       (boot/fileset-diff @prev)
                       boot/output-files)
            css-files (boot/by-ext [".css"] files)]
        (doseq [file css-files
                :let [new-file (io/file out (boot/tmp-path file))]]
          (io/make-parents new-file)
          (pod/with-call-in @pod
            (asset-minifier.core/minify-css ~(.getPath (boot/tmp-file file))
                                            ~(.getPath new-file))))
        (perun/report-info "minify-css" "Minified %s css file(s)" (count css-files))
        (-> fileset (boot/add-resource out) boot/commit!)))))

(deftask build
  "Build nicerthantriton.com"
  []
  (comp (p/global-metadata)
        (p/markdown)
        (p/slug :slug-fn slugify-filename)
        (p/permalink :permalink-fn permalinkify)
        (p/canonical-url)
        (recent-posts)
        (topics)
        (p/render :renderer 'nicerthantriton.core/page)
        (p/assortment :renderer 'nicerthantriton.core/topic :grouper tagify)
        (p/atom-feed :filterer #(= (:parent-path %) "posts/"))))

(deftask dev
  "Build nicerthantriton.com dev environment with reloading"
  []
  (comp (serve :resource-root "public/")
        (watch)
        (build)
        (reload :asset-path "/public")
        (cljs)
        #_(p/print-meta)))

(def aws-edn
  (read-string (slurp "aws.edn")))

(deftask deploy
  "Build nicerthantriton.com and deploy to production s3 bucket"
  []
  (comp (build)
        (minify-css)
        (p/inject-scripts :scripts #{"ga-inject.js"})
        (cljs :optimizations :advanced)
        (s3-sync :bucket "nicerthantriton.com"
                 :source "public"
                 :access-key (:access-key aws-edn)
                 :secret-key (:secret-key aws-edn)
                 :options {"Cache-Control" "max-age=315360000, no-transform, public"})))
