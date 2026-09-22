# Register with an avatar

Send `POST /api/users/register` as `multipart/form-data`:

| Field | Type |
| --- | --- |
| username | Text, required |
| password | Text, required |
| email | Text, optional |
| firstName | Text, optional |
| lastName | Text, optional |
| avatar | File, optional |

The service validates and uploads the avatar through `StorageService`, then stores
the returned URL in `users.avatar`. The registration response includes it in
`data.user.avatar`. A supplied empty or unsupported image is rejected by the
existing image validation. JSON registration without an avatar remains supported.

For Cloudflare R2, configure `STORAGE_PROVIDER=r2` and the existing R2 credentials,
bucket and endpoint settings. Set `R2_PUBLIC_BASE_URL` to the HTTPS public domain
of the bucket so persisted avatar URLs remain usable. Without this setting, the
storage adapter returns a presigned URL that expires.

Example using curl (replace the file path):

```sh
curl -X POST http://localhost:8080/api/users/register \
  -F 'username=example-user' \
  -F 'password=ExamplePassword123!' \
  -F 'firstName=Example' \
  -F 'avatar=@/path/to/avatar.png'
```
