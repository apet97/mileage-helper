# Structured Settings

Source: https://dev-docs.marketplace.cake.com/clockify/build/manifest/structured-settings.html

---

## Definition

Structured settings are a way for you to easily and declaratively create a UI for the settings of your add-on.

Structured settings are a great way to get started with building the settings for your add-on while ensuring they integrate flawlessly with Clockify's UI and follow the same styling guidelines as Clockify does. The structure of the settings is flexible and capable of building and supporting complex settings structures and UIs.

## Interactions

### UI

Clockify handles building and rendering the UI for the add-on settings, as well as persisting the settings whenever they are updated.

### APIs

Clockify exposes a set of APIs that add-ons can use to retrieve and update their settings for a particular workspace.

### Lifecycle

Add-ons can subscribe to the settings updated lifecycle event to be notified whenever the settings are updated.

## Properties

This section will briefly describe the structure of the settings definition. More details and possible values for a particular field can be found under the definitions list of the manifest schema for the manifest version that you are targeting.

Settings are organized by nesting tabs and groups. Values and types of the settings must be compatible.

### Tabs

Tabs are at the top level of hierarchy when defining settings.

Tabs cannot be nested in other tabs or groups, and they need to have at least one type of settings defined. If the groups property is defined, it needs to have at least one settings property defined in it.

| Property | Required | Description |
|---|---|---|
| id | yes | Tab identifier |
| name | yes | Tab name, will be displayed on the UI |
| header | no | Text shown on the settings header |
| settings | no | List of settings contained in the tab |
| groups | no | List of groups contained in the tab |

Example:

```json
"settings": {
    "tabs": [{
        "id": "Tab id",
        "name": "Tab one title",
        "header": {
            "title": "Title text"
        },
        "groups": [{
            "id": "Group id",
            "title": "Group one title",
            "description": "Group description",
            "header": {
                "title": "Header title"
            },
            "settings": [{
                "id": "Setting id",
                "name": "Default setting",
                "description": "Description of default setting",
                "placeholder": "Default setting here...",
                "type": "TXT",
                "value": "Value of default setting",
                "required": true,
                "copyable": true,
                "readOnly": false,
                "accessLevel": "ADMINS"
            }]
        }],
        "settings": [{
            "id": "Tab setting",
            "name": "Tab setting",
            "type": "TXT",
            "value": "Some value",
            "required": true,
            "accessLevel": "EVERYONE"
        }]
    }]
}
```

### Groups

Groups are a way to link related settings. Group can be part of tabs, and one tab can contain multiple groups.

| Property | Required | Description |
|---|---|---|
| id | yes | Group identifier |
| title | yes | Group title, will be displayed on the UI |
| description | no | Brief description the settings group |
| header | no | Text shown on the group header |
| settings | yes | List of settings contained in the group |

Example:

```json
"groups": [{
    "id": "Group id",
    "title": "Group one title",
    "description": "Group description",
    "header": {
        "title": "Header title"
    },
    "settings": [{
        "id": "Setting id",
        "name": "Default setting",
        "description": "Description of default setting",
        "placeholder": "Default setting here...",
        "type": "TXT",
        "value": "Value of default setting",
        "required": true,
        "copyable": true,
        "readOnly": false,
        "accessLevel": "ADMINS"
    }]
}]
```

### Settings

This is the actual settings object which defines the individual setting elements.

| Property | Required | Description |
|---|---|---|
| id | yes | Unique identifier for the settings property |
| name | yes | Property name, will be displayed on the UI |
| description | no | Brief description of the property |
| placeholder | no | Placeholder that will be displayed if the value empty |
| type | yes | Settings' type. Each type is displayed differently on the UI |
| key | no | Serves as the key for the settings when retrieved as key value pairs |
| value | yes | Settings' value. It must match with the type of settings defined in the type property |
| allowedValues | no* | List of allowed values for settings of the dropdown type. *Required if type of settings is DROPDOWN_SINGLE or DROPDOWN_MULTIPLE |
| required | no | Defines if the setting is required |
| copyable | no | Defines if a 'Copy' button will be added next to the setting value on the UI |
| readOnly | no | Defines if the setting value is read-only |
| accessLevel | yes | Defines the access level a user must have in order to access this setting |

Example:

```json
"settings": [{
    "id": "Setting id",
    "name": "Default setting",
    "description": "Description of default setting",
    "placeholder": "Default setting here...",
    "type": "TXT",
    "value": "Value of default setting",
    "required": true,
    "copyable": true,
    "readOnly": false,
    "accessLevel": "ADMINS"
}]
```

## Endpoints

Clockify exposes the following API endpoints which can be used to interact with the stored settings of an add-on. The requests to these APIs must be authenticated.

### Retrieving Settings

```
Headers:
X-Addon-Token: {token}

GET /addon/workspaces/{workspaceId}/settings
```

The endpoint requires the following parameters:

- **workspaceId** – ID of workspace where the add-on is installed, can be retrieved from the claims present in the authentication token

The settings are specific to an add-on installation on a given workspace.

Sample Response:

```json
{
    "tabs": [{
        "name": "Tab one title",
        "id": "tab one id",
        "groups": [{
            "id": "group nested in tab",
            "title": "Group title",
            "description": "group description",
            "header": {
                "title": "Header title"
            },
            "settings": [{
                "id": "Setting id",
                "name": "Default setting",
                "description": "Description of default setting",
                "placeholder": "Default setting here...",
                "type": "TXT",
                "value": "Value of default setting",
                "required": true,
                "copyable": true,
                "readOnly": false,
                "accessLevel": "ADMINS"
            }]
        }],
        "header": {
            "title": "title header 2"
        },
        "settings": [{
            "id": "Setting id 2",
            "name": "Default setting",
            "description": "Description of default setting",
            "placeholder": "Default setting here...",
            "type": "TXT",
            "value": "Value of default setting 2",
            "required": true,
            "copyable": true,
            "readOnly": false,
            "accessLevel": "ADMINS"
        }]
    }]
}
```

### Updating Settings

The following endpoint can be used to update one or more add-on settings by providing the unique settings IDs.

```
Headers:
X-Addon-Token: {token}

PATCH /addon/workspaces/{workspaceId}/settings

[
    {
         "id": "settingId",
         "value": "New value of setting"
    }
]
```
