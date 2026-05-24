# Lifecycle

Source: https://dev-docs.marketplace.cake.com/clockify/build/manifest/lifecycle.html

---

## Definition

The lifecycle of an add-on consists of all the steps beginning from when it's installed until when the add-on gets uninstalled.

Throughout its lifecycle the add-on may be in one of the two states:

- **active** — it's loaded on the Clockify UI, receives events and can interact with the Clockify API
- **inactive** — it's not loaded and cannot interact with Clockify, but it's still installed and all the user data are still kept

A general lifecycle of an add-on includes the following events:

- Installed
- Status changed
- Settings updated
- Deleted

## Types

### Installed

Add-on is installed on a workspace.

To receive this event, the add-on must declare the INSTALLED lifecycle hook as part of its lifecycles. During installation, a lifecycle event is triggered and the add-on is provided with the installation context and a set of tokens.

**It is important to note that this payload will only be supplied once for each add-on installation.**

We recommend persisting the installation payload in your database if your add-on meets, or plans to meet in the future, the use cases where an installation token may be needed.

The data included in the installation payload, including the API URLs and tokens, are dependent on the environment and the region where the add-on is installed. If you intend to interact with the Clockify APIs, you must always use the appropriate URLs for the specific environment.

Example of a payload that is sent as part of the INSTALLED event:

```
Request Headers
Content-Type : application/json
X-Addon-Lifecycle-Token : {{token}}

{
  "addonId": "62ddf9b201f42e74228efa3c",
  "authToken": "{{token}}",
  "workspaceId": "60332d61ff30282b1f23e624",
  "asUser": "60348d63df70d82b7183e635",
  "apiUrl": "{{apiUrl}}",
  "addonUserId": "1a2b3c4d5e6f7g8h9i0j1k2l",
  "webhooks": [{
     "path": "https://example.com/webhook",
     "webhookType": "ADDON",
     "authToken": "{{token}}"
  }]
}
```

The authToken is an API token that can be used to make authenticated requests to the Clockify API. For more information, read the Authentication and authorization section.

### Status changed

After installation, the add-on is automatically enabled and becomes active.

There are cases where the user may choose to deactivate an add-on instead of uninstalling it, for instance if they do not wish to use the add-on at the present but still want to preserve their settings and configurations.

To receive this event, the add-on must declare the STATUS_CHANGED lifecycle hook as part of its lifecycles.

Example of a payload that is sent as part of the STATUS_CHANGED event:

```
Request Headers
Content-Type : application/json
X-Addon-Lifecycle-Token : {{token}}

{
  "addonId": "62ddf9b201f42e74228efa3c",
  "workspaceId": "60332d61ff30282b1f23e624",
  "status": "INACTIVE"
}
```

The status values can be either **ACTIVE** or **INACTIVE**.

### Settings updated

An add-on can have its settings structure defined inside the manifest. In these cases, the add-on can subscribe to the SETTINGS_UPDATED lifecycle hook to be notified anytime one of its users updates the settings for the add-on.

Example of a payload that is sent as part of the SETTINGS_UPDATED event:

```
Request Headers
Content-Type : application/json
X-Addon-Lifecycle-Token : {{token}}

{
  "workspaceId": "60332d61ff30282b1f23e624",
  "addonId": "62ddf9b201f42e74228efa3c",
  "settings": [
    {
      "id": "txt-setting",
      "name": "Txt setting",
      "value": "Some text"
    },
    {
      "id": "link-setting",
      "name": "Link setting",
      "value": "https://clockify.me"
    },
    {
      "id": "number-setting",
      "name": "Number setting",
      "value": 5
    },
    {
      "id": "checkbox-setting",
      "name": "Checkbox setting",
      "value": true
    },
    {
      "id": "dropdown-single-setting",
      "name": "Dropdown single setting",
      "value": "option 1"
    },
    {
      "id": "dropdown-multiple-setting",
      "name": "Dropdown multiple setting",
      "value": [
        "option 1",
        "option 2"
      ]
    }
  ]
}
```

### Deleted

When an add-on is deleted from a workspace, a lifecycle event is triggered and the add-on is provided with the context for the installation that is being uninstalled. All the tokens that are provided to the add-on become invalid and from that moment on, the add-on can no longer interact with the Clockify API on behalf of the workspace user.

Example of a payload that is sent as part of the DELETED event:

```
Request Headers
Content-Type : application/json
X-Addon-Lifecycle-Token : {{token}}

{
  "addonId": "62ddf9b201f42e74228efa3c",
  "workspaceId": "60332d61ff30282b1f23e624",
  "asUser": "60348d63df70d82b7183e635"
}
```
