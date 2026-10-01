# Register with an avatar

Send `POST /api/users/register` as `multipart/form-data`:

| Part | Type |
| --- | --- |
| request | JSON part (`application/json`), required; contains `username`, `password` and optional `email`, `firstName`, `lastName` |
| avatar | File, optional |

The service validates and uploads the avatar through `StorageService`, then stores
the returned URL in `users.avatar`. The registration response includes it in
`data.user.avatar`. A supplied empty or unsupported image is rejected by the
existing image validation. Registration without an avatar still sends the JSON `request` part.

For Cloudflare R2, configure `STORAGE_PROVIDER=r2` and the existing R2 credentials,
bucket and endpoint settings. Set `R2_PUBLIC_BASE_URL` to the HTTPS public domain
of the bucket so persisted avatar URLs remain usable. Without this setting, the
storage adapter returns a presigned URL that expires.

Example using curl (replace the file path):

```sh
curl -X POST http://localhost:8080/api/users/register \
  -F 'request={"username":"example-user","password":"ExamplePassword123!","firstName":"Example"};type=application/json' \
  -F 'avatar=@/path/to/avatar.png'
```

For browser `FormData`, append `request` as a JSON `Blob` with type `application/json`. See [frontend-api-guide.md](frontend-api-guide.md) for a complete example. There is currently no public `POST /api/storage/images` endpoint.
