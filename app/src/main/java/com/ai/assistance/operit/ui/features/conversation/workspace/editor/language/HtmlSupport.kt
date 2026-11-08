package com.ai.assistance.operit.ui.features.conversation.workspace.editor.language

/**
 * HTML语言支持
 */
class HtmlSupport : BaseLanguageSupport() {
    companion object {
        private val TAGS = setOf(
            "html", "head", "title", "body", "div", "p", "span", "a", "img", "ul", "ol", "li",
            "h1", "h2", "h3", "h4", "h5", "h6", "strong", "em", "br", "hr", "meta", "link",
            "script", "style", "table", "tr", "td", "th", "form", "input", "button", "textarea",
            "select", "option", "label", "header", "footer", "nav", "section", "article", "aside",
            "main", "canvas", "audio", "video", "source", "iframe", "svg", "path", "rect", "circle",
            "line", "polyline", "polygon", "text", "g", "defs", "use", "symbol", "marker", "pattern",
            "clipPath", "mask", "filter", "feGaussianBlur", "feOffset", "feBlend", "feColorMatrix"
        )
        
        private val ATTRIBUTES = setOf(
            "id", "class", "style", "href", "src", "alt", "title", "width", "height", "type",
            "value", "name", "placeholder", "disabled", "checked", "selected", "readonly", "required",
            "maxlength", "minlength", "max", "min", "pattern", "autocomplete", "autofocus", "formaction",
            "formmethod", "formnovalidate", "formtarget", "rel", "target", "download", "colspan",
            "rowspan", "headers", "scope", "action", "method", "enctype", "accept", "accept-charset",
            "novalidate", "onsubmit", "onclick", "onchange", "onkeyup", "onkeydown", "onkeypress",
            "onmouseover", "onmouseout", "onmousedown", "onmouseup", "onload", "onerror"
        )
        
        private val FILE_EXTENSIONS = listOf("html", "htm", "xhtml")
        
        init {
            // 注册语言支持
            LanguageSupportRegistry.register(HtmlSupport())
        }
    }
    
    override fun getName(): String = "html"
    
    override fun getKeywords(): Set<String> = TAGS
    
    override fun getBuiltInFunctions(): Set<String> = ATTRIBUTES
    
    override fun getCommentStart(): List<String> = listOf("<!--")
    
    override fun getMultiLineCommentEnd(): String? = "-->"
    
    override fun getFileExtensions(): List<String> = FILE_EXTENSIONS
} 