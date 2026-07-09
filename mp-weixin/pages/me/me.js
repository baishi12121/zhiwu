"use strict";
const e=require("../../common/vendor.js"),
n=require("../../mock/mall.js"),
r=require("../../store/user.js"),
s=require("../../api/me.js");
Array||e.resolveComponent("layout-default-uni")();
const _=e.defineComponent({
  name:"ProfilePage",
  __name:"me",
  setup(){
    const userStore=r.useUserStore();
    const profile=e.ref(null);
    const orderStats=e.ref([]);
    const services=e.ref([]);

    function mergeProfile(base,info){
      if(!info||info.userId<0) return base;
      return {
        ...base,
        userId:info.userId,
        nickname:info.nickname||base.nickname,
        avatar:info.avatar||base.avatar,
        isLogged:true,
      };
    }

    async function loadAll(){
      try{
        const [p,orders,svcs]=await Promise.all([
          s.getMeProfile(),
          s.getMeOrderStats(),
          s.getMeProfileServices(),
        ]);
        profile.value=mergeProfile(p,userStore.userInfo);
        orderStats.value=orders;
        services.value=svcs;
      }catch(err){
        console.error("[me] loadAll failed",err);
      }
    }

    function goOrderList(status){
      const url=status?("/pages/order/list?status="+encodeURIComponent(status)):"/pages/order/list";
      e.index.navigateTo({url});
    }

    function goService(item){
      if(item.type==="toast"||!item.path){
        e.index.showToast({title:item.label+" 待接入",icon:"none"});
        return;
      }
      e.index.navigateTo({url:"/pages/"+item.path});
    }

    function goEditProfile(){ e.index.navigateTo({url:"/pages/me/edit-profile"}); }
    function goMessages(){ e.index.navigateTo({url:"/pages/me/messages"}); }
    function goSettings(){ e.index.navigateTo({url:"/pages/me/settings"}); }

    const nickname=e.computed(()=>profile.value?profile.value.nickname:"登录后查看");
    const memberMeta=e.computed(()=>{
      const p=profile.value;
      if(!p) return "";
      return p.memberDesc+" · 账号成长值 "+p.growth;
    });
    const tag=e.computed(()=>profile.value?profile.value.memberLevel:"PRO");
    const avatar=e.computed(()=>profile.value?profile.value.avatar:"/static/images/default-avatar.png");

    e.onShow(()=>{ loadAll(); });

    return (u,i)=>({
      a:e.o(goMessages,"__goMessages__"),
      b:e.o(goSettings,"__goSettings__"),
      c:e.o(goEditProfile,"__goEditProfile__"),
      d:e.t(e.unref(avatar)),
      e:e.t(e.unref(nickname)),
      f:e.t(e.unref(memberMeta)),
      g:e.t(e.unref(tag)),
      h:e.o(()=>goOrderList(),"__goAllOrders__"),
      i:e.f(e.unref(orderStats),(o)=>({
        a:e.t(o.value),
        b:e.t(o.label),
        c:o.status,
        f:o.value>0,
        g:e.o(()=>goOrderList(o.status),o.status),
      })),
      j:e.f(e.unref(services),(o)=>({
        a:e.t(o.icon),
        b:e.t(o.label),
        c:o.label,
        d:o.accent,
        e:o.accent+"1F",
        f:e.o(()=>goService(o),o.label),
      })),
      m:e.gei(u,"__profile__"),
    });
  },
});
const d=e._export_sfc(_,[["__scopeId","data-v-132f6888"]]);
wx.createPage(d);
