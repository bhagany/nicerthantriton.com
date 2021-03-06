(ns nicerthantriton.core
  (:require
   [goog.dom :as dom]))

(def nicer-reasons
  ["orbiting prograde"
   "hospitable to life as we know it"
   "never toots a silly conch"
   "zero parts fish"
   "none of that insufferable heralding"
   "antimisanthropic"
   "no grottoes were harmed in the making of this blog"
   "distaste for cryovolcanism"
   "geologically inactive"
   "decidedly eccentric"
   "probably will never be torn apart by tidal forces"
   "tidally unlocked"
   "definitely fluid interior"
   "albedo < 0.76"
   "well within my star's habitable zone"
   "surface temperature ideal for liquid water"
   "topologically toroidal"
   "no monarchical pretensions"])

(.addEventListener
 js/document
 "DOMContentLoaded"
 (fn []
   (let [nicer-reason-p (.getElementById js/document "nicer-reason")]
     (dom/setTextContent nicer-reason-p (rand-nth nicer-reasons)))))
