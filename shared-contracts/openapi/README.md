# Shared API contracts

The OpenAPI 3 contract is the source for API documentation and client integration. Add versioned paths and schemas here before implementing matching client features.

Keep Android and web DTOs aligned to the API contract. Do not duplicate business rules in UI code; clients may validate for usability, but the server validates every mutation.
