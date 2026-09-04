# Transfer shapes

Which merchant strings may raise a review, and which must never. `TransferShapeTest`
repeats these verbatim, the way `CorpusTest` repeats `sms-corpus.md`; this is where the
reasoning lives.

A string is transfer-shaped when the movement it names *could* have been between two
accounts the owner holds. It says nothing about whether it was — that is the question
being asked.

## Must ask

| String | Why |
|---|---|
| `EBL Account Transfer` | The ATM withdrawal and the EBL-to-EBL move both read exactly this |
| `EBL Skybanking MFS Transfer-bKash` | The owner's bKash, or a friend's |
| `Own Account Transfer` | Usually pairs on its own; asks when the partner never arrives |
| `NPSB FUND TRANSFER` | Inter-bank, either direction |
| `AC TRANSFER THROUGH EBL CONNECT` | Same |
| `bKash Cash Out` | Money to the Cash account, no second message |
| `ATM Withdrawal` | The canonical no-second-message movement |
| `Send Money` | bKash wallet to wallet |

## Must never ask

| String | Why |
|---|---|
| `UBER BANGLADESH LTD-UBER` | A ride is not a movement between accounts |
| `FOODPANDA BANGLADESH LIMITED` | Nor is dinner |
| `EBL Skybanking Mobile Recharge` | Carries none of the shapes; a recharge is spending |
| `North South University` | A fee is spending |
| `CINEPLEXBD` | Ditto |
| `null` / blank | Nothing to match on, so nothing to ask about |

The second table is the load-bearing one. On a real 1,154-row ledger, asking about
every unpaired row is 10.7 questions a month against 1.4 for these shapes alone — and
ten notifications a month about dinner gets the whole feature muted inside a week.
A muted question settles nothing.
