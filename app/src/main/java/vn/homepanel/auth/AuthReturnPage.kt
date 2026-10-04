package vn.homepanel.auth

fun authReturnPage(title: String,body: String,button: String,returnScheme: String = "homepanelmr"): String {
    require(returnScheme in setOf("homepanelmr", "homepanelmr-community", "homepanelmr-store",
        "homepanelmr-community-debug", "homepanelmr-store-debug"))
    fun html(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>HomePanel MR</title><style>body{background:#09151b;color:#eff7f4;font:20px system-ui;max-width:640px;margin:80px auto;padding:24px}a{display:inline-block;background:#93e6c2;color:#09151b;padding:18px 24px;border-radius:14px;text-decoration:none}p{line-height:1.6;color:#b6c8cc}</style></head><body><h1>${html(title)}</h1><p>${html(body)}</p><a href="$returnScheme://open">${html(button)}</a></body></html>"""
}
