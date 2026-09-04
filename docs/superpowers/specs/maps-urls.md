# Maps URL corpus

What Google Maps actually puts on the clipboard when you share a place, and what
Khata must read out of it. `MapsUrlTest` reads this file and asserts every row, so a
row added here is a test — the same shape as `sms-corpus.md`.

Conventions:

- `⏎` is a newline. Maps shares a place as a name line followed by a link line.
- An empty cell means the parser must return null for that field. A short link that
  has not been resolved genuinely has no coordinates, and storing the URL anyway is
  the point (`2026-08-26` §12).
- Inputs are verbatim. Never tidy the punctuation; it is the thing under test.

| Input | name | lat | lng |
|---|---|---|---|
| `https://www.google.com/maps/place/Sultans+Dine/@23.7461,90.3742,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d23.7461!4d90.3742` | Sultans Dine | 23.7461 | 90.3742 |
| `https://maps.google.com/?q=23.7461,90.3742` |  | 23.7461 | 90.3742 |
| `https://www.google.com/maps/search/?api=1&query=23.7461,90.3742` |  | 23.7461 | 90.3742 |
| `geo:23.7461,90.3742?q=Kacchi+Bhai` | Kacchi Bhai | 23.7461 | 90.3742 |
| `https://maps.app.goo.gl/AbCdEf` |  |  |  |
| `Sultan's Dine⏎https://maps.app.goo.gl/AbCdEf` | Sultan's Dine |  |  |
| `https://www.google.com/maps/place/X/@23.80,90.40,17z/data=!3d23.7461!4d90.3742` | X | 23.7461 | 90.3742 |

The last row is the one that matters most: `@` and `!3d!4d` disagree, and `!3d!4d`
wins. The `@` is where the map happened to be centred when the link was made; the
`!3d!4d` is the place itself. They differ whenever the user panned before sharing.
