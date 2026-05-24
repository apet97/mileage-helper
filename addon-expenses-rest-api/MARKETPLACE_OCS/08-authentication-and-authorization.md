# Authentication & Authorization

Source: https://dev-docs.marketplace.cake.com/clockify/build/authentication-and-authorization.html

---

## Basics

In order to build add-ons for the Clockify app, CAKE.com Marketplace API needs to interact with the Clockify API. For an add-on to have access to the Clockify API, every request needs to have an X-Addon-Token header with a valid add-on token.

Tokens and API keys that are provided to add-ons by Clockify are collectively called add-on tokens. Your add-on tokens along with your API keys should be kept secret. There are several types of add-on tokens that an add-on can receive, depending on the context. Each specific token has its own use cases, as described below.

## Rate limits

Requests to the Clockify API are rate limited. Please note that the limit is subject to change in the future. You can read more about rate limits on the Clockify documentation.

If the limit is exceeded, a 'Too many requests' error will be returned.

## Tokens

### Installation token

The installation token is supplied as part of the installation payload if the add-on has defined an installed lifecycle in its manifest. For more information about lifecycle requests sent to add-ons, check out the add-on lifecycle section.

This token is unique and is specific for each add-on installation on a given workspace. Each time an add-on gets reinstalled a new payload will be provided. You can obtain this token by reading authToken field of the installed lifecycle hook payload. This token has **admin privileges** in the workspace and **does not expire**.

**You must ensure the token is kept a secret and is not leaked externally as it has full access over the workspace.**

If the add-on is uninstalled from the workspace, the installation token will no longer be valid.

**Example usages:**

Installation tokens are intended to be used by the add-on's backend. They can be used in cases where full access over the workspace is required, long-running operations, reporting etc. Installation tokens can also be exchanged for user tokens for fine-grained access over a specific user.

**Exchanging for a user token:**

There may be cases where you might want to use a user token rather than an installation token, for instance when interacting with a user's profile.

To do so, Clockify exposes the following endpoint:

```
Request Headers:
Content-Type: application/json
X-Addon-Token: {installation token}

Request Endpoint:
POST {backendUrl}/addon/user/{userId}/token
```

The response body will be a string which will be the user token for the user specified by the ID. The generated user token will work the same way as the user tokens that are supplied to iframes.

### User token

The user token is supplied as a query parameter whenever a UI component or a custom settings UI is loaded into Clockify. UI components are loaded and rendered inside an iframe and the loaded URL will always contain a query parameter named **auth_token**. This token is unique to each user of the add-on on a given workspace.

The user token acts on behalf of a single user, the user that is currently viewing the UI component, and has a **lifespan of 30 minutes**. After a token expires, a new one can be requested by dispatching the appropriate window event.

As the token acts on behalf of a workspace user, it will have the same access and permissions as the user does.

Differently from the installation token, the user token will also contain claims pertaining to the user such as role, language, theme etc.

**Example usages:**

User tokens can be used on both the add-on frontend and the add-on backend.

On the frontend, through UI components, it can be used to interact and make requests to the Clockify API. All requests made to the Clockify API will be on behalf of the current user and will share the same rate-limiting quota.

On the backend, the user token can either be retrieved through the frontend or it can be retrieved by exchanging the installation token for a user token.

### Webhook signature

A webhook signature is a type of token that is provided as a signature to verify the authenticity of webhook requests.

Unlike the installation or user tokens, it cannot be used to authenticate and interact with the Clockify API. Its purpose is strictly to be used for request verification.

This token has a reduced set of claims which can be used to identify the workspace and the add-on installation for which the event was triggered. Some of the available claims for this type of token are:

- sub
- workspaceId
- addonId

## Claims

To provide more context on the environment where the add-on has been installed, both the installation and user tokens will contain a set of claims that can be used to determine the location of the Clockify API endpoints or retrieve more information about the installation.

The following set of claims is present in both installation and user tokens:

```json
{
  "backendUrl": "https://api.clockify.me/api",
  "reportsUrl": "https://reports.api.clockify.me",
  "locationsUrl": "https://locations.api.clockify.me",
  "screenshotsUrl": "https://screenshots.api.clockify.me",
  "sub": "{add-on key}",
  "workspaceId": "{workspace id}",
  "user": "{user id}",
  "addonId": "{add-on id}"
}
```

- **URL claims** — the location of the API endpoints for the environment where the add-on is installed
- **sub** — the sub field will be the same as the key that is defined in the add-on manifest. The sub is used to verify that the token was signed on behalf of your add-on.
- **workspaceId** — the ID of the workspace where the add-on is installed
- **user** — the ID of the workspace owner (for installation tokens), or the ID of the user who is currently logged in and viewing a UI component (for user tokens)
- **addonId** — the ID of the add-on installation on the workspace

The following set of claims is present only in the user token:

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

## Token Verification

All tokens signed by Clockify are JWT tokens which are signed with the **RSA256** algorithm.

Tokens that Clockify may sign include:

- installation token
- user token
- webhook signature
- lifecycle signature

The following is a checklist of the steps that need to be taken to verify that an add-on token is valid:

### Verify signature

Before accepting the token or any of its claims as valid, the signature should first be verified. To verify the token's signature, you have to use the following X509 public key which is provided below in PEM format:

```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAubktufFNO/op+E5WBWL6
/Y9QRZGSGGCsV00FmPRl5A0mSfQu3yq2Yaq47IlN0zgFy9IUG8/JJfwiehsmbrKa
49t/xSkpG1u9w1GUyY0g4eKDUwofHKAt3IPw0St4qsWLK9mO+koUo56CGQOEpTui
5bMfmefVBBfShXTaZOtXPB349FdzSuYlU/5o3L12zVWMutNhiJCKyGfsuu2uXa9+
6uQnZBw1wO3/QEci7i4TbC+ZXqW1rCcbogSMORqHAP6qSAcTFRmrjFAEsOWiUUhZ
rLDg2QJ8VTDghFnUhYklNTJlGgfo80qEWe1NLIwvZj0h3bWRfrqZHsD/Yjh0duk6
yQIDAQAB
-----END PUBLIC KEY-----
```

### Verify token expiry

The token expiry should be checked and expired tokens should be rejected.

### Verify token claims

The following claims must always match these values to verify that the token is being used for its intended purpose:

- **iss** = clockify
- **type** = addon

The iss claim denotes that the issuer is Clockify and the type claim denotes that the intended usage for this token is to be used by add-ons.

The sub claim must also match the expected event.
