# Security review of the original code base

This review covers the original project package (JSP/Servlet, MySQL 5.5, Ganache/web3j, Filebase/IPFS,
Face++ and Gmail integrations). The original code is **not** included in this repository.
The rebuild in this repository addresses the findings as described in the right-hand column.

| # | Finding in the original | Evidence (original file) | Status in this repository |
|---|---|---|---|
| 1 | **No voter or candidate passwords.** Voter login matched e-mail + assembly + district, all of which are semi-public. The password entered at registration was never stored. Candidate "passwords" were read from the district/assembly fields. | `votlog.java`, `canlog.java`, `canreg.java`, `imple.vreg/clog`, `voterreg`/`candidatereg` tables | Passwords are required (at least 10 characters) and stored as salted PBKDF2-HMAC-SHA256 hashes (210k iterations), compared in constant time. |
| 2 | **Hard-coded administrator** `admin@gmail.com` / `admin`, and the submitted password was printed to the server log. | `eclog.java` | The first admin is created from environment variables and hashed like other accounts. Nothing is logged. |
| 3 | **No server-side authorisation** on administrator pages (for example `ecmain.jsp`, `Voters.jsp`), and session attributes were set *before* the credentials were checked. | JSPs, `votlog.java`, `canlog.java` | `SecurityFilter` enforces role-based access on `/admin`, `/voter` and `/candidate`. Session attributes are set only after a successful login, and the session is renewed at login to prevent fixation. |
| 4 | **SQL injection**: queries were built by string concatenation from request parameters. | `imple.check`, `imple.ch`, `MailSendkey.java`, `Reject.jsp`, `caccept.jsp`, `vaccept.jsp`, `ved.jsp`, `votes.jsp`, `votesucessfully.jsp` | All SQL uses `PreparedStatement` parameters, with a test for injection payloads at login. |
| 5 | **Credentials in source code**: Face++ API key/secret, two Gmail addresses with app passwords, a Filebase (S3) access key/secret, an Ethereum private key, and MySQL `root`/`root`. | `face.java`, `MailSendkey.java`, `mail1.java`, `voteed.jsp`, `dbconn.java` | No secrets in code. Configuration comes from environment variables (`.env.example`). **These keys should be treated as compromised and revoked by their owner.** |
| 6 | **Double voting was not prevented by the database.** The vote handler took the voter's identity from a request parameter (`mail`). The `votes` table had no uniqueness on the voter, and "already voted" relied on status strings updated through concatenated SQL. | `voteed.jsp`, `votes` table | The voter comes from the authenticated session. A `participation(election_id, voter_user_id)` primary key and a single transaction guarantee one ballot per voter per election. A 16-thread race test is included. |
| 7 | **No ballot secrecy.** Each vote row stored the voter's e-mail, and a JSON copy including the e-mail was uploaded to public IPFS storage. | `voteed.jsp`, `votes` table | Ballots have no voter reference. Who voted and what was voted are stored separately (see Limitations). |
| 8 | **"Blockchain" hashes were not verifiable.** Block hashes were computed and then overwritten with timestamps. The Ganache transaction only stored an IPFS file name. | `voteed.jsp`, `Block.java` | Replaced with a real per-election SHA-256 hash chain over ballots, with an integrity check shown to administrators and on the results page. It is tamper-*evident*, not tamper-proof, and is not a blockchain. |
| 9 | **Logic bug**: `if (ch = true)` (assignment) made the OTP check always pass. | `MailSendkey.java` | The OTP flow was removed (see below). |
| 10 | **Personal data shipped with the code**: a 5.7 MB MySQL dump with user records and images. | `New Project 20251126 1604.sql` | Not published. A clean schema and synthetic demo data are provided instead. |
| 11 | CSRF, output encoding and security headers were absent. | JSPs | Per-session CSRF token on every POST, JSTL `<c:out>` escaping, `X-Frame-Options`, `nosniff` and CSP headers. |

## Features not carried over

Face recognition (Face++), e-mail OTP (Gmail SMTP), IPFS upload (Filebase) and the Ganache transaction
all depended on the leaked third-party credentials above and on services that cannot be tested offline.
They were removed rather than re-implemented. Re-adding any of them would need credentials supplied via
configuration and a clear privacy design; in particular, biometric data should not be sent to a third party
without consent.

## Limitations of the rebuild

- This is a teaching application. It has not been audited or penetration-tested, and it is not suitable for real elections.
- An administrator with database access can still correlate `participation` and `ballots` rows by insertion order.
  Real secret-ballot guarantees need cryptographic voting protocols.
- The hash chain detects modification or deletion of stored ballots, but not a malicious administrator who rewrites the whole chain.
  Publishing the chain head (for example after each ballot) would be needed for that.
- There is no rate limiting or account lockout on login, and no e-mail verification.
