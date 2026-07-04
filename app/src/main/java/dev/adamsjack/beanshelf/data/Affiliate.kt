package dev.adamsjack.beanshelf.data

import android.net.Uri

/**
 * Shop-link builder. "Shop online" prefers retailers with affiliate programs;
 * paste your affiliate IDs below and every link the app opens will carry them.
 * Leave an ID blank and that retailer's link is still generated, just untagged.
 */
object Affiliate {

    // ── Your affiliate IDs ────────────────────────────────────────────────
    /** Amazon Associates tracking tag, e.g. "jackadams-20". */
    const val AMAZON_TAG = ""

    /** eBay Partner Network campaign id (campid), e.g. "5338XXXXXX". */
    const val EBAY_CAMPID = ""
    // ─────────────────────────────────────────────────────────────────────

    data class Shop(val label: String, val url: String, val affiliated: Boolean)

    fun shopsFor(query: String): List<Shop> {
        val q = Uri.encode(query)
        return listOf(
            Shop(
                label = if (AMAZON_TAG.isBlank()) "Amazon" else "Amazon ✦",
                url = "https://www.amazon.com/s?k=$q" +
                    if (AMAZON_TAG.isBlank()) "" else "&tag=$AMAZON_TAG",
                affiliated = AMAZON_TAG.isNotBlank(),
            ),
            Shop(
                label = if (EBAY_CAMPID.isBlank()) "eBay" else "eBay ✦",
                url = "https://www.ebay.com/sch/i.html?_nkw=$q" +
                    if (EBAY_CAMPID.isBlank()) ""
                    else "&mkcid=1&mkrid=711-53200-19255-0&siteid=0&campid=$EBAY_CAMPID&toolid=10001&mkevt=1",
                affiliated = EBAY_CAMPID.isNotBlank(),
            ),
            Shop(
                label = "Roaster's shop (web search)",
                url = "https://www.google.com/search?q=" + Uri.encode("$query buy price"),
                affiliated = false,
            ),
        )
    }
}
