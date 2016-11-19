(set-env!
 :source-paths #{"src" "content"}
 :resource-paths #{"resources"}
 :dependencies '[[boot/core "2.6.0" :scope "provided"]
                 [pandeiro/boot-http "0.7.0"]
                 [adzerk/boot-cljs "1.7.228-2" :scope "test"]
                 [adzerk/boot-reload "0.4.12" :scope "test"]
                 [hiccup "1.0.5"]
                 [perun "0.4.0-SNAPSHOT" :scope "test"]])

(require '[adzerk.boot-cljs :refer [cljs]]
         '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-reload :refer [reload]]
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
  [n num-posts NUMPOSTS int "The number of posts to store"]
  (with-pre-wrap fileset
    (let [options (merge +recent-posts-defaults+ *opts*)
          global-meta (perun/get-global-meta fileset)
          recent (->> (perun/get-meta fileset)
                      (filter #(= (:parent-path %) "posts/"))
                      (sort-by :date-published #(compare %2 %1))
                      (take (:num-posts options)))]
      (perun/set-global-meta fileset (assoc global-meta :recent-posts recent)))))

(deftask topics
  []
  (with-pre-wrap fileset
    (let [global-meta (perun/get-global-meta fileset)
          topics (->> (perun/get-meta fileset)
                      (mapcat :tags)
                      (map #(get ntt/tag-topics %))
                      set)]
      (perun/set-global-meta fileset (assoc global-meta :topics topics)))))

(deftask dev
  []
  (comp (serve :resource-root "public/")
        (watch)
        (p/markdown)
        (p/slug :slug-fn slugify-filename)
        (p/permalink :permalink-fn permalinkify)
        (recent-posts)
        (topics)
        (p/render :renderer 'nicerthantriton.core/page)
        (p/assortment :renderer 'nicerthantriton.core/topic :grouper tagify)
        (reload :asset-path "/public")
        (cljs)
        (p/print-meta)))
