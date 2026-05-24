# Environment and Regions

Source: https://dev-docs.marketplace.cake.com/clockify/build/environments-and-regions.html

---

## Environments

Add-ons should be able to be installed on every Clockify workspace, irrespective of environment where the workspace is located.

To ensure that add-ons will work on all environments, you should avoid making use of hardcoded values for API endpoints and UI locations and instead always retrieve environment-specific values from the token claims.

Different environments where a workspace can be located are:

### Regions

Clockify workspaces can be located in each of the available regions. If a workspace is located on a region, the entirety of the data for that workspace is also located in that region. Each region exposes its own API locations.

### Subdomains

A Clockify workspace can also be located on a subdomain. Workspaces that are located on subdomains have custom UI locations. A workspace that is located on a subdomain can also be located on a specific region.

### Development environments

When testing your add-on, you may notice that the environment on which you have installed the add-on is a completely separate one. The development environments use their own separate locations for all API endpoints.

## Ensuring add-ons will work irrespective of the environment

To ensure that add-ons can work independent of the environment where they are installed, we have to retrieve all the environment-related information from the add-on authentication token which is in fact a JWT.

The following claims can be used to determine the locations where API calls must be made for a specific workspace:

```json
{
  "backendUrl": "https://api.clockify.me/api",
  "reportsUrl": "https://reports.api.clockify.me",
  "locationsUrl": "https://locations.api.clockify.me",
  "screenshotsUrl": "https://screenshots.api.clockify.me"
}
```

The above claims represent the locations of the backend, reports, locations and screenshots API services.

## Retrieving information related to the add-on installation

The add-on token carries other claims which may be used to identify the add-on installation and retrieve other useful information such as the workspace where the add-on is installed, or the user that is currently logged in.

The following claims can be used to retrieve more info related to the add-on installation:

```json
{
  "sub": "{add-on key}",
  "workspaceId": "{workspace id}",
  "user": "{user id}",
  "addonId": "{add-on id}"
}
```

- **sub** — the sub field will be the same as the key that is defined in the add-on manifest. The sub is used to verify that the token was signed on behalf of your add-on.
- **workspaceId** — the ID of the workspace where the add-on is installed
- **user** — the ID of the workspace owner (for installation tokens), or the ID of the user who is currently logged in and viewing a UI component (for user tokens)
- **addonId** — the ID of the add-on installation on the workspace

The above claims are available for both installation and user tokens.

## Retrieving information related to the add-on user

Apart from the claims which provide information related to the environment where the add-on is installed, there are also claims which provide information related to the user who is interacting with your add-on.

The following claims can be used to retrieve more info related to the user who is interacting with the add-on UI components:

```json
{
  "language": "EN",
  "theme": "DEFAULT",
  "workspaceRole": "OWNER"
}
```

- **language** — the language settings for the current user. You may use this to support multiple languages and localized content for your add-on.
- **theme** — the theme settings for the current user. It is strongly recommended that your add-on uses the same theme colors the user has configured on Clockify.
- **workspaceRole** — the role of the current user on this workspace. Learn more about roles on the Clockify API docs.

The above claims are only available for user tokens.
