(ns travcrawl.travhandler
  (:gen-class)
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.core.memoize :as memo]))

(require '[reaver :refer [parse extract extract-from attr text jsoup]])


(def autostartspar {:1 1.10
                    :2 1.10
                    :3 1.10
                    :4 1.12
                    :5 1.13
                    :6 1.10
                    :7 1.08
                    :8 1.04
                    :9 1.06
                    :10 1.07
                    :11 1.05
                    :12 1.03
                    :13 1.00
                    :14 1.00
                    :15 1.00})

(def startlistapage (slurp "https://spelatrav.se/v75"))
(def startmetodpage (slurp "https://www.travet.se/v75-startlista/"))

(defn hamtaStatistikInformation [url]
  (->>
   (client/get url {:as :json})
   (:body)))

(def cachadStatistikhamtning (memo/ttl hamtaStatistikInformation :ttl/threshold 43200000))


(defn hamtaStatistikForHast [id]
  (let [statistik (cachadStatistikhamtning (str "https://api.travsport.se/webapi/horses/statistics/organisation/TROT/sourceofdata/SPORT/horseid/" id))]
    {:segerprocent (read-string (str/replace (:winningRate statistik) #"\s" ""))
     :startpoang (read-string (str/replace (:points statistik) #"\s" ""))}))

(defn hamtaStatikForKusk [id]
  (let [statistik (cachadStatistikhamtning (str "https://api.travsport.se/webapi/licenseholder/drivers/statistics/organisation/TROT/sourceofdata/SPORT/driverid/" id))]
    statistik))


(defn parseEnHast [hast]
  (let [haststatistik (extract hast [:namn :statistikurl :spar]
                               "td" text
                               "a" (attr :href)
                               "")
                               
        hastMedId (merge haststatistik {:id (second (re-find #"visa\/(.+)\/resultat" (:statistikurl haststatistik)))})]
    (merge hastMedId (hamtaStatistikForHast (:id hastMedId)))))

(defn parseEnKusk [kusk]
  (let [kuskurl (extract kusk [:namn :statistikurl]
                         "td" text
                         "a" (attr :href))
        kuskMedId (merge kuskurl {:id (second (re-find #"visa\/(.+)\/kuskstat" (:statistikurl kuskurl)))})]
    (merge kuskMedId (hamtaStatikForKusk (:id kuskMedId)))))

(defn parseSpar [spar]
  (last (str/split spar #":")))

(defn parseAvdelningsnamn [avd]
  (second (re-find #"(V\d+-\d+)" avd)))
  
(defn parseEkipage [avdelning]
  (doall (->> (extract-from avdelning
                            "tr" [:startnummer :hast :kusk :speladprocent :spar]
                            "td:nth-child(1)" text
                            "td:nth-child(2)" jsoup
                            "td:nth-child(3)" jsoup
                            "td:nth-child(9)" text
                            "td:nth-child(4)" text)
              (map #(update-in % [:hast] parseEnHast))
              (map #(update-in % [:kusk] parseEnKusk))
              (map #(update-in % [:spar] parseSpar)))))
              

(defn parseStartplats [avdelning]
  (update-in avdelning [:hastar] parseEkipage))

(defn parseAvdelning [site]
  (->> (extract-from
        site ".row > div"
        [:avd :hastar]
        "h4" text
        "tbody > tr" jsoup)
       (map #(update-in % [:avd] parseAvdelningsnamn))
       ))


(defn totaltBerakning [hastar attSnitta]
  (float (reduce + (map #(get-in % attSnitta) hastar))))
;; => #'travcrawl.core/snittHastar

(defn totaltAvdelning [avdelning]
  (let [hastar (:hastar avdelning)]
    {:snittSegerprocentHast (totaltBerakning hastar [:berakning :segerprocentHast])
     :snittStartpoang (totaltBerakning hastar [:berakning :startpoang])
     :snittSegerprocentKusk (totaltBerakning hastar [:berakning :segerprocentKusk])
     :snittPrispengarKusk (totaltBerakning hastar [:berakning :prispengarKusk])}))

(def snitt-konstanter
  {:segerprocentHast 1.0
   :startpoang 1.0
   :segerprocentKusk 0.9
   :prispengarKusk 0.9})

(defn beraknaSnittForHast [snittForAvdelning hast start]
  (let [snitt  {:jmfSegerprocentHast (* (:segerprocentHast snitt-konstanter) (/ (get-in hast [:berakning :segerprocentHast] hast) (:snittSegerprocentHast snittForAvdelning)))
                :jmfStartpoang (* (:startpoang snitt-konstanter) (/ (get-in hast [:berakning :startpoang] hast) (:snittStartpoang snittForAvdelning)))
                :jmfSegerprocentKusk (* (:segerprocentKusk snitt-konstanter) (/ (get-in hast [:berakning :segerprocentKusk] hast) (:snittSegerprocentKusk snittForAvdelning)))
                :jmfPrispengarKusk (* (:prispengarKusk snitt-konstanter) (/ (get-in hast [:berakning :prispengarKusk] hast) (:snittPrispengarKusk snittForAvdelning)))}
        beraknadVinst (/ (reduce + (vals snitt)) (count (vals snitt)))
        sparjusterad (case start
                       "Auto" (* beraknadVinst ((keyword (:spar hast)) autostartspar))
                       "Volt" beraknadVinst
                       beraknadVinst)]
    (assoc snitt :beraknadVinst sparjusterad)))

(defn getDataForEkipage [ekipage]
  {:segerprocentHast (get-in ekipage [:hast :segerprocent])
   :startpoang (get-in ekipage [:hast :startpoang])
   :segerprocentKusk (:winningRates (first (get-in ekipage [:kusk :statistics])))
   :prispengarKusk (:prizeMoney (first (get-in ekipage [:kusk :statistics])))})


(defn calculateForAvdelning [avdelning]
  (assoc avdelning :hastar (map #(assoc %
                                        :berakning (getDataForEkipage %)) (:hastar avdelning))))

(defn beraknaSnitten [avdelning]
  (let [snittForAvdelning (totaltAvdelning avdelning)
        startmetod (:startmetod avdelning)]
    (assoc avdelning :hastar (map #(assoc % :berakning (beraknaSnittForHast snittForAvdelning % startmetod)) (:hastar avdelning)))))

(defn sort-by-supersnitt [avdelning]
  (assoc avdelning :hastar (reverse (sort-by #(get-in % [:berakning :beraknadVinst]) (:hastar avdelning)))))

(defn display-results [avdelning]
  {:avdelning (:avd avdelning)
   :start (:startmetod avdelning)
   :predictions (map #(do
                        {:startnummer (:startnummer %)
                         :hast (get-in % [:hast :namn])
                         :kusk (get-in % [:kusk :namn])
                         :data (getDataForEkipage %)
                         :speladprocent (:speladprocent %)
                         :beraknadVinst (format "%.2f" (* 100 (get-in % [:berakning :beraknadVinst])))})
                     (:hastar avdelning))})


(defn narrow-down [avdelning]
  {:avdelning (:avdelning avdelning)
   :hastar (take 2 (:predictions avdelning))})


(defn fetchAndSave []
  (spit "data.txt" (->>
                    (parse startlistapage)
                    (parseAvdelning)
                    (map parseStartplats)
                    (prn-str))))

(defn hamtaStartmetoder []
  (->> (extract-from (parse startmetodpage) ".startlistor > form > h3"
                     [:startmetod]
                     "h3 > a" text)
       (map :startmetod)
       (map #(str/split % #", "))
       (map #(do {:avd (first %) :start (last (str/split (last %) #" "))}))))


(defn appendStartMetod [avd startmetoder]
  (let [startmethodForAvd (first (filter #(str/includes? (:avd avd) (:avd %)) startmetoder))]
    (if (nil? startmethodForAvd)
      (assoc avd :startmetod "N/A")
      (assoc avd :startmetod (val (second startmethodForAvd))))))

(defn appendStartmetoder [avdelningar]
  (let [startmetoder (hamtaStartmetoder)]
    (map #(appendStartMetod % startmetoder) avdelningar)))

(defn calculateForAvdelningar [avdelningar]
  (doall (->>
          (map calculateForAvdelning avdelningar)
          (map beraknaSnitten)
          (map display-results))))


(defn fetchAvdelning []
  (doall (->>
          (parse startlistapage)
          (parseAvdelning)
          (appendStartmetoder)
          (pmap parseStartplats))))

(defn preFetchedAvdelningar []
  (->>
   (read-string (slurp "data.txt"))))


(defn preFetchedCalculate []
  (->>
   (read-string (slurp "data.txt"))
   (map calculateForAvdelning)
   (map beraknaSnitten)
   (map sort-by-supersnitt)
   (map display-results)
   (map narrow-down)))

(defn fetchAndCalculate []
  (->>
   (parse startlistapage)
   (parseAvdelning)
   (map parseStartplats)
   (map calculateForAvdelning)
   (map beraknaSnitten)
   (map sort-by-supersnitt)
   (map display-results)
   (map narrow-down)))


(comment 
  (preFetchedCalculate)
  
  )