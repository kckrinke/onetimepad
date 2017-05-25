#  OneTimePad
![OneTimePad Icon](./media/icon.png)

OneTimePad is based on OTP Authenticator, a two-factor authentication App for Android 4.0+.

It implements Time-based One-time Passwords (TOTP) like specified in RFC 6238. Simply scan the QR code (or enter the setup code manually) and login with the generated 6-digit code.

## Features:
- Free and Open-Source
- Requires minimal permissions
  - Only camera access for QR code scanning
- Encrypted storage on Android 4.3+
- Sleek minimalistic Material Design
- Great Usability 
- Compatible with Google Authenticator
- Manual setup (for devices where the camera is not available)

## Backups:
- When you're ready to backup your secrets, from the main menu select "Export Secrets"
- Exporting will make a copy of the internal datastore and place it in your SD Card's Documents directory, ie: /sdcard/Documents/OneTimePad.data
- When the app is first launched, if that file exists, you will be prompted to import the backup with a yes/no dialog
- If you don't want to import the datastore, rename or move the file somewhere else (like, say, an off-device backup location)
- If you do import the backup, you will be asked to re-launch the app after the import is completed
- This feature needs lots of testing!

## License:
```
Copyright (C) 2016-2017 Kevin C. Krinke
Copyright (C) 2015 Bruno Bierbaumer
Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in the
Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
OR OTHER DEALINGS IN THE SOFTWARE.
```
