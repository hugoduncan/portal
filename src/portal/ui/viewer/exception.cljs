(ns portal.ui.viewer.exception
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as str]
            [portal.colors :as c]
            [portal.ui.icons :as icon]
            [portal.ui.inspector :as ins]
            [portal.ui.styled :as s]
            [portal.ui.theme :as theme]
            [reagent.core :as r]))

(spec/def ::cause string?)

(spec/def ::trace-line
  (spec/cat :class symbol?
            :method symbol?
            :file (spec/or :str string? :nil nil?)
            :line number?))

(spec/def ::trace (spec/coll-of ::trace-line))

(spec/def ::type symbol?)
(spec/def ::message string?)
(spec/def ::at ::trace-line)

(spec/def ::via
  (spec/coll-of
   (spec/keys :req-un [::type ::message ::at])))

(spec/def ::exception
  (spec/keys :req-un [::cause ::trace ::via]))

(defn exception? [value]
  (spec/valid? ::exception value))

(defn- inspect-sub-trace [trace]
  (r/with-let [expanded? (r/atom (zero? (:index (first trace))))]
    (let [theme (theme/use-theme)
          {:keys [clj? sym class file]} (first trace)]
      [:<>
       [(if @expanded?
          icon/chevron-down
          icon/chevron-right)
        {:size "sm"
         :style
         {:grid-column "1"
          :width       (* 3 (:padding theme))
          :color       (::c/border theme)}}]
       [s/div
        {:on-click #(swap! expanded? not)
         :style
         {:grid-column "2"
          :cursor      :pointer}}

        [s/span
         {:title file
          :style {:color (if clj?
                           (::c/namespace theme)
                           (::c/package theme))}}
         (if-not clj? class (namespace sym))]]
       [s/span
        {:style
         {:grid-column "3"
          :color       (::c/border theme)}}
        " [" (count trace) "]"]
       (when @expanded?
         (for [{:keys [clj? sym method line index]} trace]
           [:<>
            {:key index}
            [s/div]
            [s/div
             (if clj?
               (name sym)
               [s/div method])]
            [ins/inspector line]]))])))

(defn- analyze-trace-item [index trace]
  (let [[class method file line] trace
        clj-name (demunge class)
        clj? (or (str/ends-with? file ".clj")
                 (not= clj-name class))]
    (merge
     {:class  class
      :method method
      :file   file
      :line   line
      :index  index}
     (when clj?
       {:clj? true
        :sym  clj-name}))))

(defn- inspect-stack-trace [trace]
  (let [theme (theme/use-theme)]
    [s/div
     {:style
      {:display :grid
       :grid-template-columns "auto 1fr auto"
       :align-items :center
       :grid-gap [0 (:padding theme)]}}
     (->> trace
          (map-indexed
           analyze-trace-item)
          (partition-by :file)
          (map
           (fn [trace]
             ^{:key (hash trace)}
             [inspect-sub-trace trace])))]))

(defn- inspect-via [via]
  (let [theme (theme/use-theme)
        {:keys [type message]} (first via)]
    [:<>
     [s/div
      {:style {:text-align :center}}
      [s/span
       {:style
        {:font-weight :bold
         :margin-right (:padding theme)
         :color (::c/exception theme)}}
       type]
      message]]))

(defn inspect-exception [value]
  (let [theme (theme/use-theme)]
    [s/div
     {:style
      {:background (::c/background2 theme)
       :margin "0 auto"
       :padding (:padding theme)
       :box-sizing :border-box
       :color (::c/text theme)
       :font-size  (:font-size theme)
       :border-radius (:border-radius theme)
       :border [1 :solid (::c/border theme)]}}
     [s/div
      {:style
       {:margin [0 :auto]
        :width  :fit-content}}
      [inspect-via (:via value)]
      [inspect-stack-trace (:trace value)]]]))

(def viewer
  {:predicate exception?
   :component inspect-exception
   :name :portal.viewer/ex})
