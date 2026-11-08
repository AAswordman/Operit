# Completion Protocol

The Worker creates an `awaiting_callback` transaction with a PKCE verifier, a hashed one-time delivery credential, and a validated completion redirect URL.

GitHub calls the Worker callback. After a successful code exchange and payload encryption, the Worker redirects the browser with only `transactionId` and `status=complete`. Cancellation and exchange errors are also represented as completion statuses and the transaction is deleted.

The completion URL never carries a GitHub token or delivery credential. The application owns URL transport for its own browser surface. It prepares the allowed callback destination before the Worker transaction begins, presents the authorization URL, accepts only a navigation to that destination, and returns that completed URL to Core.

The application prepares its destination before Core starts the transaction. Core keeps the opaque transaction ID and delivery credential in client-private storage, then compares the returned destination and transaction ID before calling `POST /oauth/github/claim`. The Worker deletes the transaction during a successful claim, so no completion polling endpoint exists.

[DONE]
