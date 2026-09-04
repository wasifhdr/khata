# TMDB Response Corpus — `/3/search/multi`

The fixtures behind `TmdbParseTest`, in the same role `sms-corpus.md` plays for the parser:
this file is the source of truth, and the test copies each block verbatim.

**Provenance, stated plainly:** these are **hand-written to TMDB's documented response shape**,
not captured from a live call — no API key existed when the module was built. They are therefore
evidence about the parser, not about TMDB. If a real response ever disagrees with one of these,
the real response wins and the fixture is corrected to match it.

Fields are trimmed to the ones the module reads. TMDB returns considerably more on every row,
and everything absent here is absent because nothing stores it: no overview, no cast, no genres,
no runtime, no popularity, no backdrop.

---

## F1. A film

`title` and `release_date`, and `media_type` of `movie`.

```json
{"page":1,"results":[{"id":949,"media_type":"movie","title":"Heat","original_title":"Heat","release_date":"1995-12-15","vote_average":7.9,"vote_count":7421,"poster_path":"/umSVjVdbVwtx5ryCA2QXL44Durm.jpg","adult":false}],"total_results":1}
```

Yields `TmdbResult(949, "Heat", 1995, FILM, 7.9, "/umSVjVdbVwtx5ryCA2QXL44Durm.jpg")`.

---

## F2. A series

The trap this file exists for: a series carries **`name` and `first_air_date`**, not `title` and
`release_date`. Reading the film pair on a series yields a blank name rather than an error, so
nothing would throw — the module would simply record every show as untitled.

```json
{"page":1,"results":[{"id":1438,"media_type":"tv","name":"The Wire","original_name":"The Wire","first_air_date":"2002-06-02","vote_average":8.6,"vote_count":1502,"poster_path":"/4lbclFySvugI51fwsyxBTOm4DqK.jpg","origin_country":["US"]}],"total_results":1}
```

Yields `TmdbResult(1438, "The Wire", 2002, SERIES, 8.6, "/4lbclFySvugI51fwsyxBTOm4DqK.jpg")`.

---

## F3. A person, which must be dropped

`search/multi` returns people alongside titles. Nothing in this module holds a person, and a
person row has no `title`, no `name` field this module wants, and no date at all.

```json
{"page":1,"results":[{"id":1158,"media_type":"person","name":"Al Pacino","known_for_department":"Acting","popularity":24.5,"profile_path":"/2dNqZmDLmVeUmnfWvzGjZoLZ2Xh.jpg"}],"total_results":1}
```

Yields nothing.

---

## F4. No poster, no date

Both are normal. An unreleased title has an empty `release_date`, and plenty of real rows carry
`poster_path: null`. Neither is an error, and neither may be shown as a zero or a broken image.

```json
{"page":1,"results":[{"id":123456,"media_type":"movie","title":"An Unfinished Film","release_date":"","vote_average":0.0,"vote_count":0,"poster_path":null}],"total_results":1}
```

Yields `TmdbResult(123456, "An Unfinished Film", null, FILM, null, null)` — note the rating is
`null` rather than `0.0`: nobody has rated it, which is not the same as everybody hating it.

---

## F5. A mixed page, which is the usual case

One query, three kinds of row. The person is dropped and the order of the rest is preserved.

```json
{"page":1,"results":[{"id":949,"media_type":"movie","title":"Heat","release_date":"1995-12-15","vote_average":7.9,"poster_path":"/umSVjVdbVwtx5ryCA2QXL44Durm.jpg"},{"id":1158,"media_type":"person","name":"Al Pacino","profile_path":"/2dNqZmDLmVeUmnfWvzGjZoLZ2Xh.jpg"},{"id":1438,"media_type":"tv","name":"The Wire","first_air_date":"2002-06-02","vote_average":8.6,"poster_path":"/4lbclFySvugI51fwsyxBTOm4DqK.jpg"}],"total_results":3}
```

Yields two results, `Heat` then `The Wire`.

---

## F6. Nothing found

An empty array, which is the same outcome as no key and no network: the manual fields.

```json
{"page":1,"results":[],"total_pages":1,"total_results":0}
```

---

## F7. Not JSON at all

A truncated body, an HTML error page from a proxy, or an empty response. Each parses to an empty
list rather than throwing — a crash on a bad response would take the screen with it.

```
{"page":1,"resu
```
