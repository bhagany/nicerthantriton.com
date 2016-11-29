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

(defn design [data title content]
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
     [:title title]
     [:script {:defer (= :prod (-> data :meta :tier)) :src "/js/main.js" :type "text/javascript"}]]
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
       [:div#bio
        [:img {:src "/images/nicer-guy.jpg" :width "150px" :height "150px"}]
        [:p "I'm Brent Hagany, CTO of Goodsie. My free time is spent learning things or tinkering with some side project or another. Nicer than Triton is my outlet for going into more detail than strictly necessary (or maybe desirable) on the subjects of programming, astronomy, math, photography, or whatever grabs my attention next. If that sounds entertaining, stay in touch in some of the ways below."]]
       [:div#sidebar-content
        social-ul
        (when-let [topics (-> data :meta :topics seq)]
          [:div.topics
           [:h3 "Topics"]
           [:ul.topics
            (map #(-> [:li [:a.topic {:class (slugify %) :href (str "/" (topic-href %))} %]])
                 topics)]])
        (when-let [posts (-> data :meta :recent-posts seq)]
          [:div.recent
           [:h3 "Recent Posts"]
           [:ul.recent
            (map #(-> [:li [:a {:href (:permalink %)} (:title %)]])
                 posts)]])
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
  (let [tags (-> data :entry :tags)
        main-topic (or (get tag-topics (first tags)) "General")
        title (str (-> data :entry :title) " | Nicer than Triton")
        content [[:h2 {:class (str "title " (slugify main-topic))}
                  (-> data :entry :title)]
                 (-> data :entry :content)
                 [:img.star {:src "/images/star.svg" :width "32px" :height "32px"}]
                 (when-let [date-published (-> data :entry :date-published)]
                   (let [df (java.text.SimpleDateFormat. "d MMMM yyyy")]
                     (.setTimeZone df (java.util.TimeZone/getTimeZone "UTC"))
                     [:p.posted (.format df date-published)]))
                 [:p.tags
                  (map #(let [topic (get tag-topics %)]
                          [:a {:href (str "/" (topic-href topic)) :class (str (slugify topic) " tag")} %])
                       tags)]]]
   (design data title content)))

(defn topic
  [data]
  (let [topic (-> data :entry :topic)
        title (str topic " | Nicer than Triton")
        content [[:h2 {:class (str "title " (slugify topic))} topic]
                 [:ul
                  (map #(-> [:li [:a {:href (:permalink %)} (:title %)]])
                       (-> data :entries))]]]
    (design data title content)))
