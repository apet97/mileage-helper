# Window Events

Source: https://dev-docs.marketplace.cake.com/clockify/build/window-events.html

---

## Window messages

Clockify uses the window message API in order to allow add-on developers to receive messages about specific events and react accordingly.

Clockify supports two-way event communications, where the add-on can subscribe to specific events as well as dispatch events that should trigger actions on the Clockify site.

## Event subscription

Below is a sample snippet showing how to register a listener for an event:

```javascript
handleWindowMessage = (message) => {
  console.log(message.data.title);
}

window.addEventListener("message", (event) => handleWindowMessage(event));
```

### Events

Events will contain the following fields:

- `message.data.title`
- `message.data.body`

The title field will be the name of the event. The body field will be an optional payload which depends on the event type.

Current events that can be listened for are:

- **URL_CHANGED** — `message.data.body={the URL}`
- **TIME_ENTRY_STARTED**
- **TIME_ENTRY_CREATED**
- **TIME_ENTRY_STOPPED**
- **TIME_ENTRY_DELETED**
- **TIME_ENTRY_UPDATED**
- **TIME_TRACKING_SETTINGS_UPDATED**
- **WORKSPACE_SETTINGS_UPDATED**
- **PROFILE_UPDATED**
- **USER_SETTINGS_UPDATED**

The above events are not final and are subject to change in the future.

## Event dispatch

In addition to listening for events that Clockify dispatches, the add-on can also interact with Clockify by triggering its own events.

Current events that can be dispatched from the add-on are:

### refreshAddonToken

Asks Clockify to refresh the add-on token for the user that is currently viewing the UI component. Learn more about tokens and their contexts on the Authentication & Authorization section.

### preview

If dispatched from an add-on, it will ask Clockify to open a modal with the add-on's marketplace listing.

### navigate

Asks Clockify to navigate to the location specified by the type parameter. It requires the following payload:

```json
{
  "type": "tracker"
}
```

The following is a list of supported navigation locations:

- tracker

### toastrPop

Asks Clockify to show custom toast messages on the UI. It requires the following payload:

```json
{
  "type": "info" | "warning" | "success" | "error",
  "message": "your message"
}
```

Toast messages will be shown on the bottom-right section of the screen. The color of the background depends on the message type.

### Javascript Example

The following code example asks Clockify to display an error toast message:

```javascript
window.top?.postMessage(JSON.stringify({ action: "toastrPop", payload: { type: "error", message: "Your add-on toast message" } }), "*");
```
