"use strict";function n(e){return"token"in e&&!("refreshToken"in e)}function i(e){return"accessToken"in e&&"refreshToken"in e}exports.isDoubleTokenRes=i;exports.isSingleTokenRes=n;
