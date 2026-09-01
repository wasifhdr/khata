# SMS Corpus — bKash & EBL

Real messages supplied by the user on 2026-09-02, transcribed from screenshots.
This is the source of truth for both the initial `parsing_rules` set and the
parser's test corpus.

**Privacy:** third-party sender phone numbers have been replaced with
structurally identical placeholders (`01626205357` → `01700000001`, etc.) because
this file is committed to a repository that has been pushed to GitHub. They are
other people's numbers and the parser does not need the real digits — only the
shape. The user's own account and card fragments are left as the bank already
masks them. Real numbers must never be committed here.

---

## EBL — sender `EBL`

### E1. Account debit / credit — the dominant format

```
AC 115***352 is debited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 58.56 Thanks. EBL Helpline 16230
AC 112***286 is credited with BDT 60 as Own Account Transfer on 01-SEP-26 06:46:08 PM Balance is BDT 5004.2 Thanks. EBL Helpline 16230
AC 112***286 is debited with BDT 5000 as EBL Account Transfer on 01-SEP-26 07:03:20 PM Balance is BDT 4.2 Thanks. EBL Helpline 16230
AC 115***352 is credited with BDT 17316 as AC TRANSFER THROUGH EBL CONNECT on 01-SEP-26 07:34:56 PM Balance is BDT 17374.56 Thanks. EBL Helpline 16230
AC 115***352 is debited with BDT 50 as EBL Skybanking Mobile Recharge on 30-AUG-26 04:28:45 PM Balance is BDT 1219.56 Thanks. EBL Helpline 16230
AC 112***286 is credited with BDT 10000 as NPSB FUND TRANSFER on 01-SEP-26 10:38:57 AM Balance is BDT 10034.2 Thanks. EBL Helpline 16230
AC 112***286 is debited with BDT 5090 as EBL Account Transfer on 01-SEP-26 11:07:10 AM Balance is BDT 4944.2 Thanks. EBL Helpline 16230
AC 112***286 is debited with BDT 420 as EBL Skybanking MFS Transfer-bKash on 17-AUG-26 06:57:20 PM Balance is BDT 6724.19 Thanks. EBL Helpline 16230
AC 112***286 is debited with BDT 6500 as Own Account Transfer on 18-AUG-26 01:20:19 PM Balance is BDT 34.2 Thanks. EBL Helpline 16230
AC 115***352 is credited with BDT 6500 as Own Account Transfer on 18-AUG-26 01:20:20 PM Balance is BDT 6516.56 Thanks. EBL Helpline 16230
AC 115***352 is debited with BDT 330 as EBL Account Transfer on 05-AUG-26 05:44:02 PM Balance is BDT 8923.56 Thanks. EBL Helpline 16230
AC 115***352 is debited with BDT 6500 as EBL Account Transfer on 06-AUG-26 12:05:07 AM Balance is BDT 2423.56 Thanks. EBL Helpline 16230
```

Shape: `AC <acct> is (debited|credited) with BDT <amount> as <description> on <DD-MMM-YY hh:mm:ss AM|PM> Balance is BDT <balance> Thanks.`

Notes:
- Month case varies: `SEP`, `AUG` uppercase here, but `Aug` mixed-case in card messages.
- Balance may have one decimal (`5004.2`), two (`17374.56`), or none.
- `<description>` is free text and is the merchant/purpose field.

### E2. Card purchase

```
Purchase txn BDT 1101 from TOUR DE CYCLIST Ut.Card 539280**3432 on 31-Aug-26 06:27:44 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230
Purchase txn BDT 232 from foodibd.com Dhaka .Card 539280**3432 on 25-Aug-26 06:27:38 PM BST.Your A/C 115**9352 Balance BDT 1269.56. EBL Helpline 16230
Purchase txn BDT 2407 from TOKYO KITCHEN UTTA.Card 539280**3432 on 07-Aug-26 10:51:50 PM BST.Your A/C 115**9352 Balance BDT 118.56. EBL Helpline 16230
```

Shape: `Purchase txn BDT <amount> from <merchant>.Card <card> on <datetime> BST.Your A/C <acct> Balance BDT <balance>.`

Notes:
- **The merchant/`.Card` boundary is the hard part.** Merchant may end in a space
  (`foodibd.com Dhaka .Card`) or run straight into it (`TOUR DE CYCLIST Ut.Card`).
  Merchant text may itself contain a period (`foodibd.com`). Match non-greedily up
  to the last ` .Card` / `.Card`.
- Account is masked **differently here** — `115**9352` vs `115***352` in E1. Same
  account, two masks. Account matching must tolerate both.

### E3. ATM cash withdrawal

```
Cash WD BDT5000 from North South Universi. Card 539280**3432 on 19-Aug-26 06:00:37 PM BST.Your A/C 115**9352 Balance BDT 1501.56. EBL Helpline 16230
```

Notes: **`BDT5000` has no space** after `BDT`. Location is truncated by the bank.

### E4. Card-rail fund transfer

```
EBL CARDS: NPSB Fund Transfer BDT 180 using Card 452017**1835 on 18-Aug-26 12:44:55 AM.Your A/C 112**0286 Balance BDT 6534.2. Thank You. EBL Helpline 16230
```

Notes: a **second card** (`452017**1835`) on a different account.

### E5. IGNORE — carries an amount but is not a transaction

```
The OTP request is for a transaction at foodibdcom. To complete your transaction BDT 232.00 at foodibdcom with Card#539***432, use OTP: 823876 . Helpline: 16230
Ref no : 398595SI1764909024 , Your Standing Instruction Execution Status is: Successfully Executed . Helpline 16230.
```

---

## bKash — sender `bKash`

### B1. Received money

```
You have received Tk 325.00 from 01700000001. Fee Tk 0.00. Balance Tk 527.09. TrxID DHL8NM6BYC at 21/08/2026 12:35
You have received Tk 142.00 from 01700000001. Fee Tk 0.00. Balance Tk 356.09. TrxID DHA9BTWHE1 at 10/08/2026 22:10
You have received Tk 142.00 from 01700000002. Fee Tk 0.00. Balance Tk 498.09. TrxID DHA5BUPNF1 at 10/08/2026 22:27
You have received Tk 100.00 from 01700000003. Ref A. Fee Tk 0.00. Balance Tk 238.09. TrxID DHG5IGY2ZP at 16/08/2026 18:47
You have received Tk 66.00 from 01700000004. Fee Tk 0.00. Balance Tk 131.09. TrxID DHK6MX34WC at 20/08/2026 18:39
You have received Tk 71.00 from 01700000005. Fee Tk 0.00. Balance Tk 202.09. TrxID DHK2MX5HZA at 20/08/2026 18:39
You have received Tk 650.00 from 01700000006. Fee Tk 0.00. Balance Tk 1,083.20. TrxID DH94A7139O at 09/08/2026 17:33
```

Notes: an optional `Ref <x>.` segment appears between sender and `Fee`.

### B2. Payment — and the duplicate-message trap

```
Payment of Tk 564.11 is being reserved for Uber Bangladesh Ltd-Uber. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11
Payment of Tk 564.11 to Uber Bangladesh Ltd-Uber is successful. Balance Tk 214.09. TrxID DHA3BH4G8R at 10/08/2026 18:11
```

**Both messages carry the same `TrxID`.** They are one transaction announced twice.
Without TrxID dedup every Uber ride is double-counted.

```
Payment of Tk 856.00 to FOODPANDA BANGLADESH LIMITED is successful. Balance Tk 41.98. TrxID DHV41FIPGY at 31/08/2026 19:01
Payment of Tk 750.00 to CINEPLEXBD is successful. Balance Tk 338.33. TrxID DH2918VO8R at 02/08/2026 00:38
Payment of Tk 2,600.00 to CINEPLEXBD is successful. Balance Tk 338.33. TrxID DH2118Z3S1 at 02/08/2026 00:44
```

Notes: **amounts carry thousands separators** (`Tk 2,600.00`) and so do balances
(`Tk 1,027.11`).

### B3. Deposit from bank — a cross-institution transfer

```
You have received deposit from iBanking of Tk 2,600.00 from Eastern Bank PLC. Internet Banking. Fee Tk 0.00. Balance Tk 2,938.33. TrxID DH2718YM43 at 02/08/2026 00:43
```

This is the bKash side of an EBL debit. Pairs with an E1 `EBL Skybanking MFS
Transfer-bKash` or similar.

### B4. Digital loan and cashback

```
You have received Digital Loan Tk 900.00 from City Bank. Balance Tk 897.98. TrxID DHV31FH6VD at 31/08/2026 19:00.
You have received Digital Loan Tk 500.00 from City Bank. Balance Tk 1,024.21. TrxID DHN2PFGRRC at 23/08/2026 10:55.
Congratulations! You have received Cashback Tk 2.90. Balance Tk 1,027.11. TrxID DHN4PFHA3Y at 23/08/2026 10:55. Cashback on Loan!
```

### B5. IGNORE — carries an amount but is not a transaction

```
Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.750.00 to Software Shop Limited-RM51177 is 816523. Expires in 2 min.
Do NOT share your OTP or PIN with anyone. Your bKash OTP for PAYMENT of Tk.2,600.00 to Software Shop Limited-RM51177 is 746654. Expires in 2 min.
Do NOT share your OTP or PIN with anyone. Your bKash OTP to enable AUTO DEBIT in App or Website of Foodpanda Bangladesh Limited is 372914. Expires in 5 min.
You have received Loan of Tk 900.00 from City Bank in your bKash Account. Your first repayment of TK 309.28 is due on 28/09/2026.
Your Account Binding request for FOODPANDA BANGLADESH LIMITED is successful. You have authorized FOODPANDA BANGLADESH LIMITED to debit your account for future purchases. For queries, please call 16247.
```

Note `Tk.750.00` — a period between `Tk` and the digits, a third amount format.

---

## Derived requirements

1. **IGNORE rules run at the highest priority, before any amount-extracting rule.**
   OTP messages contain a real amount and a real merchant and would otherwise parse
   as perfect transactions. Getting this wrong silently doubles spending.
2. **Dedupe on `TrxID`.** bKash announces one payment twice ("being reserved", then
   "is successful") with an identical TrxID.
3. **Three amount formats:** `BDT 60`, `BDT5000` (no space), `Tk 2,600.00`
   (separators), `Tk.750.00` (period). One amount matcher must handle all.
4. **Two datetime formats:** EBL `01-SEP-26 06:46:08 PM` (month case varies),
   bKash `21/08/2026 12:35`.
5. **Two account masks for the same account:** `115***352` (E1) and `115**9352`
   (E2/E3). Matching must normalise.
6. **The user has two EBL accounts** — one ending `352`, one ending `286` — plus at
   least two cards (`539280**3432`, `452017**1835`). Seed data currently assumes a
   single EBL account and must be revised.
7. **Own Account Transfer pairs are ideal transfer-detection fodder:** equal amount,
   two accounts, timestamps within a second (`01:20:19` / `01:20:20`).
8. **Balance is present in nearly every message** — reconciliation has ground truth
   on almost every transaction, not occasionally.
