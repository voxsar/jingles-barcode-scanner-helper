# jingles-barcode-scanner-helper

Android app for capturing Bluetooth/HID barcode scanner input, optionally adding:

- expiry date
- manufacture date
- item quantity
- location

The app lets the operator configure:

- a submission URL for scan payloads
- a locations URL for loading branch → floor → shelf → box hierarchies, plus direct floor/shelf roots

Scans are sent as JSON with the barcode and any optional values that were filled in.
