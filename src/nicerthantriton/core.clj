(ns nicerthantriton.core
  (:require [clojure.string :as str]
            [hiccup.page :as hp]
            [io.perun.core :as perun]))

(def topic-tags
  {"Space" ["Analemma"]
   "Programming" ["Clojure"]
   "Math" ["Geometry" "Linear Algebra"]})

(def tag-topics
  (->> topic-tags
       (mapcat (fn [[c ts]]
                 (map #(-> [% c]) ts)))
       (into {})))

(def topic-order
  ["Programming"
   "Space"
   "Math"])

(defn slugify
  [i]
  (when (seq i)
    (->> (str/split i #"[-\.\s]")
         (str/join "-")
         str/lower-case)))

(defn topic-href
  [topic]
  (str "topics/" (slugify topic) ".html"))

(def social-ul
  [:ul.social
   [:li.twitter [:a {:href "https://twitter.com/bhagany"} "@bhagany"]]
   [:li.github [:a {:href "https://github.com/bhagany"} "bhagany"]]
   [:li.atom [:a {:href "/atom.xml"} "Subscribe"]]
   [:li.email [:a {:href "http://eepurl.com/cqwJNP"} "Updates"]]])

(defn design [data content]
  (hp/html5
   [:html
    [:head
     [:link {:rel "apple-touch-icon" :sizes "180x180" :href "/apple-touch-icon.png"}]
     [:link {:rel "icon" :type "image/png" :href "/favicon-32x32.png" :sizes "32x32"}]
     [:link {:rel "icon" :type "image/png" :href "/favicon-16x16.png" :sizes "16x16"}]
     [:link {:rel "manifest" :href "/manifest.json"}]
     [:link {:rel "mask-icon" :href "/safari-pinned-tab.svg" :color "#5bbad5"}]
     [:link {:href "https://fonts.googleapis.com/css?family=Della+Respira|Open+Sans:400,400i,700,700i"
             :rel "stylesheet"}]
     [:link {:href "/css/ntt.css" :rel "stylesheet"}]
     [:meta {:name "apple-mobile-web-app-title" :content "Nicer than Triton"}]
     [:meta {:name "application-name" :content="Nicer than Triton"}]
     [:meta {:name "theme-color" :content "#ffffff"}]
     [:script {:src "/js/main.js" :type "text/javascript"}]]
    [:body
     [:div#wrapper
      [:div#header
       [:a.logo {:href "/"}
        [:img {:src "/images/ntt-logo.svg" :width "108px" :height "108px"}]
        [:h1 "Nicer than Triton"]]
       [:div.barb-hr.down.left
        [:div.barb]
        [:div.hr]]]
      (into [:div#content] content)
      [:div#sidebar
       [:div.barb-hr.up.right
        [:div.hr]
        [:div.barb]]
       [:p "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged."]
       [:div#sidebar-content
        social-ul
        [:div.topics
         [:h3 "Topics"]
         [:ul.topics
          (map #(-> [:li [:a.topic {:class (slugify %) :href (str "/" (topic-href %))} %]])
               (-> data :meta :topics))]]
        [:div.recent
         [:h3 "Recent Posts"]
         [:ul.recent
          (map #(-> [:li [:a {:href (:permalink %)} (:title %)]])
               (-> data :meta :recent-posts))]]
        [:div.barb-hr.down.right
         [:div.hr]
         [:div.barb]]
        [:div#flair
         [:img.sidebar-logo {:src "/images/ntt-logo-outline.svg" :width "78px" :height "70px"}]
         [:p#nicer-reason]]]]
      [:div#footer
       [:div.barb-hr.up.right
        [:div.hr]
        [:div.barb]]
       [:div#footer-content
        [:a.logo {:href "/"}
         [:img {:src "/images/ntt-logo-outline.svg" :width "78px" :height "70px"}]
         [:h1 "Nicer than Triton"]]
        social-ul]]]]]))

(defn page [data]
  (let [content [[:h2 {:class (str "title " (slugify (get tag-topics (-> data :entry :tags first))))}
                  (-> data :entry :title)]
                 (-> data :entry :content)
                 [:img.star {:src "/images/star.svg" :width "32px" :height "32px"}]
                 (if-let [date-published (-> data :entry :date-published)]
                   (let [df (java.text.SimpleDateFormat. "d MMMM yyyy")]
                     (.setTimeZone df (java.util.TimeZone/getTimeZone "UTC"))
                     [:p.posted (.format df date-published)]))
                 [:p.tags
                  (map #(let [topic (get tag-topics %)]
                          [:a {:href (str "/" (topic-href topic)) :class (str (slugify topic) " tag")} %])
                       (-> data :entry :tags))]]]
   (design data content)))

(defn topic
  [data]
  (let [topic (-> data :entry :topic)
        content [[:h2 {:class (str "title " (slugify topic))} topic]
                 [:ul
                  (map #(-> [:li [:a {:href (:permalink %)} (:title %)]])
                       (-> data :entries))]]]
    (design data content)))
