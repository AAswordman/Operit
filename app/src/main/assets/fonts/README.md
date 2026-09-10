# Status card Material Symbols font

`material_symbols_rounded_static.woff2` is Google's complete Material Symbols
Rounded v370 static instance, distributed unchanged under the accompanying
[Apache 2.0 license](MATERIAL_SYMBOLS_LICENSE.txt).

Source: [Google Fonts CSS2 request](https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,1,0&display=block).
The immutable font URL and SHA-256 are recorded next to `ICON_FONT_ASSET` in
`StatusCardHtmlDocument.kt`. Runtime documents inline the bundled bytes; these
URLs document provenance and are never requested by the renderer.

The four axes are fixed at `opsz=24`, `wght=400`, `FILL=1`, `GRAD=0`. No icon-name
subset is applied: historical cards and installed prompt tags may use any legal
Material Symbols name, including `shopping_cart` outside the former 140 icons.
The static WOFF2 occupies 456,052 bytes versus 20,844 bytes for that subset,
an asset increase of 435,208 bytes. The same v370 full four-axis variable WOFF2
is 5,360,840 bytes; the fixed instance removes its unused interpolation data
while retaining the renderer's chosen style. These are font asset sizes, not a
measurement of a completed APK.

Verification with fontTools found 4,277 icon-name GSUB ligatures and no `fvar`,
`gvar`, `avar`, `HVAR` or `MVAR` tables. HarfBuzz shapes `shopping_cart` to one
`shopping_cart.fill` glyph, matching the corresponding icon codepoint; it also
passes the same check for `favorite`, `emoji_emotions` and `arrow_forward`.

`StatusCardHtmlDocumentTest` checks offline embedding, ligature CSS, historical
`shopping_cart` content and the vetted full-font hash. When updating the asset,
verify the GSUB ligature coverage and static font tables before updating the hash.
