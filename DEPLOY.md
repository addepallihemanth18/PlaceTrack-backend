# Deploy PlaceTrack

The backend and frontend are separate Git repositories. Create the Render backend from the backend repository's root `render.yaml`; deploy the frontend from its repository to Vercel.

## Aiven MySQL

1. Create an Aiven MySQL service and wait for it to become available.
2. Copy the host, port, database name, username, and password from Aiven's connection information.
3. Use a JDBC URL in Render's `DB_URL` variable in this form:

   `jdbc:mysql://HOST:PORT/DATABASE?sslMode=REQUIRED`

   Replace the uppercase values with Aiven's values. `sslMode=REQUIRED` encrypts the connection without a separate CA file.

## Render backend

1. Push this backend repository to GitHub.
2. In Render, choose **New > Blueprint**, connect the backend repository, and apply its `render.yaml`.
3. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` using the Aiven values.
4. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` to the initial administrator login. Use a unique, strong password and keep it private. These values seed an administrator only if that email does not already exist.
5. Render generates `JWT_SECRET`; leave its generated value in place.
6. Wait for deploy to finish, then visit `https://YOUR-API.onrender.com/health`. It should return `ok`.

## Vercel frontend

1. Push the frontend repository to GitHub.
2. In Vercel, import the frontend repository. Set the project root to `./` (the repository already contains the frontend app), framework preset to **Vite**, build command to `npm run build`, and output directory to `dist`.
3. Add `VITE_API_URL` with value `https://YOUR-API.onrender.com/api`, using the backend URL from the previous section.
4. Deploy or redeploy the project. Open the Vercel URL and test sign-in and data loading.

The frontend's `vercel.json` rewrites app routes to `index.html`, so refreshing a route works. The API allows CORS requests from any origin. It authenticates protected endpoints using bearer tokens and does not use cookie authentication.
