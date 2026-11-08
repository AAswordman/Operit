package com.ai.assistance.operit.util.text

import com.ai.assistance.operit.core.tools.ComputerPageInfoNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object HtmlParserUtil {

    fun getExtractionScript(): String {
        return """
            (function() {
                const IGNORE_TAGS = new Set(['SCRIPT', 'STYLE', 'META', 'LINK', 'HEAD', 'NOSCRIPT']);
                const INTERACTIVE_TAGS = new Set(['A', 'BUTTON', 'INPUT', 'TEXTAREA', 'SELECT', 'OPTION']);
                const IMPORTANT_ATTRIBUTES = new Set([
                    'id', 'class', 'href', 'src', 'alt', 'title', 'type', 'value',
                    'placeholder', 'name', 'role', 'aria-label', 'onclick'
                ]);
                const MAX_DEPTH = 20;
                const MAX_ELEMENTS = 500; // Increased limit
                
                let interactionIdCounter = 1;
                let processedElements = 0;
                const interactionMap = {};

                function findTopmostModal() {
                    let maxZ = -1;
                    let topmostModal = null;
                    const elements = document.querySelectorAll('div, section, aside, form');
                    const modalKeywords = ['modal', 'dialog', 'popup', 'overlay'];

                    for (const el of elements) {
                        try {
                            const style = window.getComputedStyle(el);
                            const zIndex = parseInt(style.zIndex, 10) || 0;
                            const position = style.position;
                            const classList = Array.from(el.classList);

                            // --- Enhanced Modal Detection Logic ---
                            let score = 0;
                            if (position === 'fixed' || position === 'absolute') score += 2;
                            if (zIndex > 100) score += 2;
                            if (el.getAttribute('role') === 'dialog') score += 3;
                            if (el.getAttribute('aria-modal') === 'true') score += 3;
                            if (modalKeywords.some(keyword => classList.some(cls => cls.includes(keyword)))) score += 2;

                            // A high score strongly indicates a modal. We also check visibility and compare z-index.
                            if (score >= 5 && isVisible(el) && zIndex >= maxZ) {
                                maxZ = zIndex;
                                topmostModal = el;
                            }
                        } catch (e) { /* Ignore elements that might cause errors */ }
                    }
                    return topmostModal;
                }

                function getCssSelector(el) {
                    if (!(el instanceof Element)) return '';
                    let path = [];
                    while (el.nodeType === Node.ELEMENT_NODE) {
                        let selector = el.nodeName.toLowerCase();
                        if (el.id) {
                            selector += '#' + el.id.trim().replace(/\s+/g, ' ');
                            path.unshift(selector);
                            break;
                        } else {
                            // Use all valid classes. Modern frameworks generate complex but often stable (within a session) class names.
                            // Filtering them with heuristics is more likely to break things than to help.
                            let classes = Array.from(el.classList || []).filter(c => /^[a-zA-Z0-9-_]+$/.test(c));
                            if (classes.length > 0) {
                                selector += '.' + classes.join('.');
                            } else {
                                // Fallback to nth-of-type if no classes are found at all.
                            let sib = el, nth = 1;
                            while (sib = sib.previousElementSibling) {
                                if (sib.nodeName.toLowerCase() === selector) nth++;
                            }
                            if (nth !== 1) selector += `:nth-of-type(${'$'}{nth})`;
                            }
                        }
                        path.unshift(selector);
                        el = el.parentNode;
                    }
                    return path.join(' > ');
                }
                
                function isVisible(elem) {
                     if (!(elem instanceof Element)) return false;
                     const style = window.getComputedStyle(elem);
                     if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
                     const rect = elem.getBoundingClientRect();
                     return rect.width > 0 && rect.height > 0;
                }

                function isInteractive(elem) {
                    if (!(elem instanceof Element)) return false;
                    const tagName = elem.tagName.toUpperCase();
                    const style = window.getComputedStyle(elem);

                    // Any INPUT element is considered interactive.
                    // The specific type ('text', 'button', 'search', etc.) is determined later.
                    return INTERACTIVE_TAGS.has(tagName) ||
                           elem.hasAttribute('onclick') ||
                           style.cursor === 'pointer';
                }

                function getNodeDescription(element) {
                    const attrs = ['aria-label', 'alt', 'title', 'placeholder', 'name'];
                    for (const attr of attrs) {
                        const val = element.getAttribute(attr);
                        if (val) return val.trim();
                    }

                    // Prioritize direct text content.
                    let directText = Array.from(element.childNodes)
                        .filter(n => n.nodeType === Node.TEXT_NODE)
                        .map(n => n.textContent.trim())
                        .join(' ')
                        .trim();
                    
                    if (directText) return directText;

                    // Fallback to textContent, which includes children, but keep it short.
                    let fullText = (element.textContent || "").replace(/\s+/g, ' ').trim();

                    // To avoid a parent's description stealing the unique text of its interactive children,
                    // we try to subtract their text from the parent's full text.
                    const children = element.children || [];
                    for(const child of children) {
                        if(isInteractive(child)) {
                            const childText = (child.textContent || "").replace(/\s+/g, ' ').trim();
                            if(childText && fullText.includes(childText)) {
                                fullText = fullText.replace(childText, '').trim();
                            }
                        }
                    }

                    return fullText.length > 80 ? fullText.substring(0, 77) + '...' : fullText;
                }

                function simplifyNode(element, depth) {
                    if (depth > MAX_DEPTH || processedElements >= MAX_ELEMENTS || !element.tagName || IGNORE_TAGS.has(element.tagName.toUpperCase()) || !isVisible(element)) {
                        return null;
                    }
                    
                    let children = [];
                    // ALWAYS process children, regardless of whether the parent is interactive.
                    // This fixes the issue where an interactive container would hide its interactive children.
                    if (element.childNodes) {
                        element.childNodes.forEach(child => {
                            if (child.nodeType === Node.ELEMENT_NODE) {
                                const simplifiedChild = simplifyNode(child, depth + 1);
                                if (simplifiedChild) {
                                    children.push(simplifiedChild);
                                }
                            }
                        });
                    }
                    
                    processedElements++;

                    const isItselfInteractive = isInteractive(element);
                    const flatChildren = children.flat();
                    const hasInteractiveDescendant = flatChildren.some(c => c.interactionId != null);
                    const ownDescription = getNodeDescription(element).trim();
                    
                    // Pruning: If a node isn't interactive, has no text, and has no interactive children, it's just a layout div. Discard it.
                    if (!isItselfInteractive && !ownDescription && !hasInteractiveDescendant) {
                        return flatChildren;
                    }

                    const tagName = element.tagName.toUpperCase();
                    let type = "container";
                     if (isItselfInteractive) {
                         if (tagName === 'A') type = 'link';
                         else if (tagName === 'BUTTON') type = 'button';
                         else if (tagName === 'INPUT') {
                            const inputType = element.getAttribute('type')?.toLowerCase();
                            type = (inputType === 'button' || inputType === 'submit') ? 'input_button' : 'input_text';
                         }
                         else if (tagName === 'TEXTAREA' || tagName === 'SELECT') type = 'input_text';
                         else type = 'interactive';
                    }

                    let interactionId = null;
                    if (isItselfInteractive) {
                        interactionId = interactionIdCounter++;
                        interactionMap[interactionId] = getCssSelector(element);
                    }

                    const finalNode = {
                        interactionId: interactionId,
                        type: type,
                        description: ownDescription || (isItselfInteractive ? type : ''), // Fallback description
                        children: flatChildren
                    };
                    
                    return [finalNode];
                }
                
                const rootElement = findTopmostModal() || document.body;
                const simplifiedTree = simplifyNode(rootElement, 0);

                return JSON.stringify({
                    tree: simplifiedTree,
                    map: interactionMap
                });
            })();
        """.trimIndent()
    }

    @Serializable
    private data class ExtractionResult(
        val tree: List<ComputerPageInfoNode>?,
        val map: Map<String, String>
    )

    fun parseAndSimplify(jsonString: String, updateInteractionMap: (Map<Int, String>) -> Unit): ComputerPageInfoNode? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val result = json.decodeFromString<ExtractionResult>(jsonString)
            
            val interactionMapIntKeys = result.map.mapKeys { it.key.toIntOrNull() ?: -1 }.filterKeys { it != -1 }
            updateInteractionMap(interactionMapIntKeys)
            
            // The result from the new script is a list (or a single root object in a list)
            // We can wrap it in a root node for consistency with the old structure if needed
            val rootChildren = result.tree ?: emptyList()
            
            return ComputerPageInfoNode(
                interactionId = null,
                type = "container",
                description = "Root",
                children = rootChildren.filterNotNull()
            )
        } catch (e: Exception) {
            // Log the exception
            println("Error parsing simplified HTML: ${e.message}")
            null
        }
    }
} 