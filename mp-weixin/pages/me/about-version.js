"use strict";
Page({
  goBack(){
    const pages=getCurrentPages();
    if(pages.length>1){
      wx.navigateBack();
    }else{
      wx.switchTab({url:"/pages/me/me"});
    }
  },
});
