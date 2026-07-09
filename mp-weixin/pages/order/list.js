"use strict";
const mock=require("../../mock/mall.js");
const api=require("../../api/me.js");

const STATUS_LABELS={unpaid:"待付款",unshipped:"待发货",unreceived:"待收货",unreviewed:"待评价",refund:"退款/售后"};
const FALLBACK_STATUS=["unpaid","unshipped","unreceived"];

Page({
  data:{
    activeStatus:"",
    tabs:[{status:"",label:"全部"}],
    orders:[],
    filteredOrders:[],
    statusLabels:STATUS_LABELS,
  },

  onLoad(q){
    const s=(q&&q.status)||"";
    if(api.MALL_ORDER_STATUS_VALUES&&api.MALL_ORDER_STATUS_VALUES.indexOf(s)>=0){
      this.setData({activeStatus:s});
    }
  },

  onShow(){
    const _this=this;
    api.getMeOrderStats().then(function(stats){
      const tabs=[{status:"",label:"全部"}].concat(stats.map(function(s){return {status:s.status,label:s.label};}));
      const orders=mock.mallProducts.slice(0,3).map(function(p,i){
        return {
          id:p.id,
          title:p.title,
          spec:p.label+" · 标准规格",
          price:p.price,
          priceText:mock.formatPrice(p.price),
          status:FALLBACK_STATUS[i%3],
          image:p.colors[0],
        };
      });
      _this.setData({tabs:tabs,orders:orders},function(){_this.applyFilter();});
    }).catch(function(){_this.applyFilter();});
  },

  applyFilter(){
    const s=this.data.activeStatus;
    const list=s?this.data.orders.filter(function(o){return o.status===s;}):this.data.orders;
    this.setData({filteredOrders:list});
  },

  selectTab(e){
    const s=e.currentTarget.dataset.status||"";
    this.setData({activeStatus:s},function(){this.applyFilter();});
  },

  goPay(e){
    const id=e.currentTarget.dataset.id;
    wx.showToast({title:"支付 "+id+" 待接入",icon:"none"});
  },

  goHome(){
    wx.switchTab({url:"/pages/index/index"});
  },
});
