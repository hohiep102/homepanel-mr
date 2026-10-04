# Public website

HomePanel MR's home, privacy, and support pages are served directly by Cloudflare Pages at <https://homepanel-mr.pages.dev/>. They do not proxy to an application server.

## Build

Run from the repository root with Python 3:

```sh
python3 scripts/build-public-site.py
python3 -m http.server 8080 --directory site/homepanel-mr
```

Preview <http://localhost:8080/>. The generated `site/` directory is ignored by Git. The generator contains the reviewed public text and writes six files: the three pages, stylesheet, `404.html`, and Cloudflare `_headers`. It needs no account credentials or external packages.

## Deploy

The `homepanel-mr` Pages project uses Direct Upload. In Cloudflare's **Workers & Pages → homepanel-mr → Create deployment**, upload `site/homepanel-mr` (or a ZIP of its contents) to Production. Keep `index.html` and `_headers` at the upload root.

An already-authorized Wrangler session can deploy the same folder:

```sh
wrangler pages deploy site/homepanel-mr --project-name homepanel-mr --branch main
```

A Git push alone does not deploy this Direct Upload project. Do not add credentials to the repository. For a hostname change, update the generator's `base`, in-app links in `LegalInfo.kt`, the READMEs, and the repository homepage.

## Verify

- Home, `/privacy/`, `/support/`, and `/style.css` must return HTTP 200 over HTTPS.
- An unknown path must return HTTP 404, not the homepage.
- Check the rendered desktop and narrow layout, navigation, privacy host disclosure, and canonical URLs.
- Check CSP, `X-Frame-Options`, `X-Content-Type-Options`, and `Referrer-Policy` on the deployed pages. A local Python preview does not apply `_headers`.
- Keep redirects from previously published URLs while older app releases and Store submissions still use them.

The site contains no added JavaScript, analytics, account forms, or application backend. Hosting request processing is described in the privacy page.
