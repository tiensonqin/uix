(ns uix.ssr-test
  (:require [clojure.test :refer [deftest is testing]]
            [uix.compiler.alpha :as uixc]
            [uix.dom.alpha :as dom]
            [clojure.string :as str]
            #?@(:cljs [[fs :as fs]
                       ["react-dom/server" :as dom-server]])
            #?@(:clj [[clojure.java.shell :as shell]
                      [clojure.java.io :as io]
                      [clj-diffmatchpatch :as diff]])))

#?(:cljs
   (set! (.-ReactDOMServer js/global) dom-server))

#?(:clj
   (defn render [el]
     (uixc/render-to-static-markup el)))

#?(:clj
   (deftest test-class-names
     (is (= (render [:div]) "<div></div>"))
     (testing "Named types"
       (is (= (render [:div {:class "a"}]) "<div class=\"a\"></div>"))
       (is (= (render [:div {:class :a}]) "<div class=\"a\"></div>")))
     (testing "Collection of classes"
       (is (= (render [:div {:class [1 2 3]}]) "<div class=\"1 2 3\"></div>"))
       (is (= (render [:div {:class [1 :a "b"]}]) "<div class=\"1 a b\"></div>")))
     (testing "Map of class -> boolean"
       (is (= (render [:div {:class {:c1 true :c2 false}}]) "<div class=\"c1\"></div>")))))

;; Adapted from https://github.com/tonsky/rum/blob/gh-pages/test/rum/test/server_render.cljc

(defn comp-simple []
  [:div
   [:div "A"
    [:span "A1"]
    [:span "A2"]]
   [:div "B"]
   [:div "C" "D"]
   [:div "E"
    [:span "E1"]]
   [:div nil]
   [:div nil "F"]
   [:div {} ((constantly nil)) "G"]])

(defn comp-tag []
  [:div.header#up "test"])


(defn comp-list []
  [:ul [:li {:key "F"}] [:li {:key "M"}]])


(defn comp-lists []
  [:div
   [:.a (list ^{:key "b"} [:.b])]
   [:.c "d" (list ^{:key "e"} [:.e])]
   [:.f "g" (list ^{:key "i"} [:.h]) "i"]])


(defn comp-root-array []
  (list
    [:.a "A"]
    [:.b "B"]
    [:.c "C"]))

(defn comp-header []
  [:ul.nav__content
   (list [:li.menu-item {:key "F"} "Женщинам"]
         [:li.menu-item {:key "M"} "Мужчинам"])
   [:li.menu-item {:key "outlet"} "Outlet"]])


(defn comp-nil1 []
  "In this case nil will be not counted against reactid"
  [:div {:class "parent"}
   nil
   [:div.child]])


(defn comp-nil2 []
  "In this case *both* nils will be counted against reactid"
  [:div {:class "parent"}
   nil
   [:div.child]
   (list
     nil
     [:div.child2])])

(defn comp-nothing []
  nil)

(defn comp-nothing2 []
  [:div
   [:div [comp-nothing]]
   [:div "a" [comp-nothing]]
   [:div [comp-nothing] "b"]
   [:div "a" [comp-nothing] "b" [:span "x"]]
   [:div [:.a] [comp-nothing] [:.b]]
   [:div ^{:key "K"} [comp-nothing]]])

(defn comp-span []
  [:span
   "a" "b"
   "a" [:tag "b"] "c"
   "a" (list "b" "c") "d"
   "a" (list "b" "c") (list "d" "e") "f"
   (list "a" "b") [:tag "c"] (list "d" "e")
   "a" nil "b"
   "a" [comp-nothing] "b"
   "a" (list nil) "b"])

(defn comp-campaign []
  [:div#today.content.wrapper
   (list
     [:div.banner {:class " big "
                   :style {:background-image "url(123)"}
                   :key "campaign-20871"}]
     [:a.banner__item-link {:href "/catalogue/s-10079-colin-s/"}]

     [:div.banner {:class " "
                   :key "banner-:promo"}]
     [:a.banner__item-link {:href nil, :target "_blank"}]

     [:div.banner {:class " medium "
                   :style {:background-image "url(321)"}
                   :key "campaign-20872"}]
     [:a.banner__item-link {:href "/catalogue/s-10089-rinascimento/"}])])

(defn comp-styles []
  [:div
   [:div.a {:style {}}]
   [:div.b {:style {:background-color nil}}]
   [:div.c {:style {:background-color ""}}]
   [:div.d {:style
            {:background-image "url(\"123\")"               ;; should escape quotes
             :line-height 24                                ;; unitless, should not add 'px'
             :-webkit-box-flex 3                            ;; prefixed unitless
             :margin-top 17                                 ;; should add 'px'
             :margin-right 17.1
             :margin-left 0}}]                              ;; no 'px' added to 0
   [:div.e {:style
            {:border-width " 1  "                           ;; trim  numeric & append 'px'
             :padding-right " 1.2 "
             :padding-bottom "1em"                          ;; do not add 'px' if unit already specified
             :text-align " left  "                          ;; trim non-numeric values
             :flex-grow " 1  "}}]                           ;; trim unitless values
   [:div.f {:style
            {:background-image "url('123')"                 ;; should escape quotes
             :fontWeight 10                                 ;; should convert from react-style properties to CSS
             "WebkitFlex" 1                                 ;; prefixed react-style prop
             "msFlex" 1                                     ;; prefixed react-style prop (lowecase ms)
             "zIndex" 1}}]])                                ;; accept strings too

(defn comp-attrs []
  [:div
   (for [[a v] [[:data-attr-ibute "b"]                      ;; should not touch data-* and aria* attr names
                [:aria-checked "c"]
                [:form-enc-type "text/plain"]               ;; should normalize (remove dashes)
                [:checked false]                            ;; nil and false attrs not printed
                [:allow-full-screen true]                   ;; true printed as attr=""
                [:href "/a=b&c=d"]]]
     ^{:key v}
     [:div {a v}])])

(defn comp-attrs-capitalization []
  [:div
   (for [a [:accept-charset :access-key :allow-transparency :auto-complete :cell-padding :cell-spacing :char-set :class-id :content-editable :context-menu :cross-origin :date-time :enc-type :form-action :form-enc-type :form-method :form-target :frame-border :href-lang :http-equiv :input-mode :key-params :key-type :margin-height :margin-width :max-length :media-group :min-length :radio-group :referrer-policy :spell-check :src-doc :src-lang :src-set :tab-index :use-map :auto-capitalize :auto-correct :auto-save :item-prop :item-type :item-id :item-ref]]
     ^{:key a} [:div {a "_"}])

   [:table
    [:td {:col-span 1
          :row-span 1}]]

   [:svg
    (for [a [:allow-reorder :attribute-name :attribute-type :auto-reverse :base-frequency :base-profile :calc-mode :clip-path-units :content-script-type :content-style-type :diffuse-constant :edge-mode :external-resources-required :filter-res :filter-units :glyph-ref :gradient-transform :gradient-units :kernel-matrix :kernel-unit-length :key-points :key-splines :key-times :length-adjust :limiting-cone-angle :marker-height :marker-units :marker-width :mask-content-units :mask-units :num-octaves :path-length :pattern-content-units :pattern-transform :pattern-units :points-at-x :points-at-y :points-at-z :preserve-alpha :preserve-aspect-ratio :primitive-units :ref-x :ref-y :repeat-count :repeat-dur :required-extensions :required-features :specular-constant :specular-exponent :spread-method :start-offset :std-deviation :stitch-tiles :surface-scale :system-language :table-values :target-x :target-y :view-box :view-target :x-channel-selector :xlink-actuate :xlink-arcrole :xlink-href :xlink-role :xlink-show :xlink-title :xlink-type :xml-base :xmlns-xlink :xml-lang :xml-space :y-channel-selector :zoom-and-pan]]
      [:path {a "_"}])]

   (for [a [:allow-full-screen :auto-play :form-no-validate :no-validate :read-only :item-scope]]
     ^{:key a} [:div {a true}])])

(defn comp-attrs-order []
  [:div
   [:a {:title "a"
        :alt "b"
        :rel "c"
        :target "d"
        :src "e"}]
   [:a {:src "a"
        :target "b"
        :rel "c"
        :alt "d"
        :title "e"}]
   [:a {:title "a" :class "b" :rel "d"}]
   [:a {:title "a" :class ["b" "c"] :rel "d"}]
   [:a.clazz {:title "a" :class "b" :rel "d"}]
   [:a.clazz {:title "a" :class ["b" "c"] :rel "d"}]
   [:a.clazz#id {:title "a"}]
   [:a#id.clazz {:title "a"}]
   [:a.clazz#id {:title "a" :class "b"}]
   [:a#clazz.id {:title "a" :class "b"}]])

(defn comp-classes []
  [:div
   [:div {:class [nil]}]
   [:div {:class :c3}]
   [:div {:class [:c3 :c4]}]                                ;; list form
   [:div {:class "c3"}]                                     ;; string form
   [:div {:class ["c3" "c4"]}]
   [:div {:class [" c3  " "  c4 "]}]                        ;; trimming
   [:div {:class [:c3 nil :c4]}]                            ;; nils are not removed
   [:div {:class [:c2 :c3]}]                                ;; removing duplicates
   [:.c1 {:class nil}]
   [:.c1 {:class (when false "...")}]                       ;; see #99
   [:.c1.c2 {:class :c3}]
   [:.c1.c2 {:class [:c3 :c4]}]                             ;; list form
   [:.c1.c2 {:class "c3"}]                                  ;; string form
   [:.c1.c2 {:class ["c3" "c4"]}]
   [:.c1.c2 {:class [" c3  " "  c4 "]}]                     ;; trimming
   [:.c1.c2 {:class [:c3 nil :c4]}]                         ;; nils are not removed
   [:.c1.c2 {:class [:c2 :c3]}]])                           ;; not removing duplicates


(defn comp-html []
  [:div {:dangerouslySetInnerHTML {:__html "<span>test</span>"}}])

(defn comp-inputs []
  [:div
   [:input#id {:class "x" :type "text" :auto-complete "off"}]
   [:input {:type "text" :default-value "x"}]
   [:input {:type "checkbox" :default-checked true}]
   [:input {:type "radio" :default-checked true}]
   [:select {:default-value "A"}
    [:option {:value "A"} "Apple"]
    [:option {:value "B"} "Banana"]]
   [:select {:value "A"}
    [:option#id.class {:value "A"} "Apple"]
    [:option#id.class {:value "B"} "Banana"]]
   [:textarea {:value "text"}]
   [:textarea "text"]
   [:textarea {:default-value "text"}]])

(defn comp-svg []
  [:svg.cclogo
   {:width 100
    :height 100
    :view-box "0 232.5 333.2 232.5"                         ;; should be rendered as viewBox
    :vector-effect "effect"                                 ;; should be rendered as vector-effect
    :version "1.1"
    :dangerouslySetInnerHTML {:__html "[...tons of raw SVG removed...]"}}])


(defn comp-aria []
  [:div
   {:aria-hidden true
    :aria-readonly false
    :aria-disabled "true"
    :aria-checked "false"}])

(def components
  {"simple" comp-simple
   "tag" comp-tag
   "list" comp-list
   "lists" comp-lists
   "root-array" comp-root-array
   "header" comp-header
   "nil1" comp-nil1
   "nil2" comp-nil2
   "nothing" comp-nothing
   "nothing2" comp-nothing2
   "span" comp-span
   "campaign" comp-campaign
   "styles" comp-styles
   "attrs" comp-attrs
   "attrs-cap" comp-attrs-capitalization
   "attrs-order" comp-attrs-order
   "classes" comp-classes
   "html" comp-html
   "inputs" comp-inputs
   "svg" comp-svg
   "aria" comp-aria})

(def render-dir "server_render_test")

#?(:cljs
   (defn -main [& args]
     (doseq [[name f] components]
       (let [html (dom/render-to-string [f])
             path (str render-dir "/html/" name ".html")]
         (fs/writeFileSync path html))
       (let [html (dom/render-to-static-markup [f])
             path (str render-dir "/markup/" name ".html")]
         (fs/writeFileSync path html)))))

#?(:clj
   (defn exec [& cmd]
     (testing cmd
       (println "Running" (str "\"" (str/join " " cmd) "\""))
       (let [{:keys [exit out err]} (apply shell/sh cmd)]
         (is (= exit 0))
         (when-not (str/blank? err)
           (binding [*out* *err*]
             (println err)))
         (when-not (str/blank? out)
           (println out))))))

#?(:clj
   (defn diff [s1 s2]
     (->> (diff/wdiff s1 s2)
          (map (fn [[op text]]
                 (case op
                   :delete (str "\033[37;41;1m" text "\033[0m")
                   :insert (str "\033[37;42;1m" text "\033[0m")
                   :equal text)))
          (str/join))))

#?(:clj
   (deftest test-ssr-compat-between-jvm-and-js
     (doseq [^java.io.File f (reverse (file-seq (io/file render-dir)))]
       (when (.exists f)
         (.delete f)))
     (.mkdir (io/file render-dir))
     (.mkdir (io/file render-dir "html"))
     (.mkdir (io/file render-dir "markup"))
     (exec "clojure" "-A:test" "-m" "cljs.main" "-m" "uix.ssr-test" "-re" "node")
     (doseq [[name f] components]
       (testing name
         (let [react-html (slurp (str render-dir "/html/" name ".html"))
               uix-html (uixc/render-to-string [f])]
           (is (= react-html uix-html) (diff react-html uix-html)))
         (let [react-html (slurp (str render-dir "/markup/" name ".html"))
               uix-html (uixc/render-to-static-markup [f])]
           (is (= react-html uix-html) (diff react-html uix-html)))))))
