<script setup lang="ts">
import { type GlobalThemeOverrides } from 'naive-ui'

// 知物商城 Admin · Naive UI 主题覆盖
// 统一 Design Token 与全局 CSS 变量保持一致
const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#18A058',
    primaryColorHover: '#36AD73',
    primaryColorPressed: '#0F8A4C',
    primaryColorSuppl: '#18A058',
    infoColor: '#2080F0',
    infoColorHover: '#4098FC',
    infoColorPressed: '#0C63D4',
    successColor: '#18A058',
    successColorHover: '#36AD73',
    successColorPressed: '#0F8A4C',
    warningColor: '#F59E0B',
    warningColorHover: '#FBAE2C',
    warningColorPressed: '#D1810A',
    errorColor: '#D03050',
    errorColorHover: '#DE5165',
    errorColorPressed: '#B01F3F',

    bodyColor: '#F5F7F9',
    cardColor: '#FFFFFF',
    modalColor: '#FFFFFF',
    popoverColor: '#FFFFFF',

    textColorBase: '#1F2937',
    textColor1: '#1F2937',
    textColor2: '#667085',
    textColor3: '#98A2B3',
    textColorDisabled: '#C5C9D1',

    borderColor: '#E8EAED',
    dividerColor: '#EEF0F2',
    tableHeaderColor: '#F8FAFC',

    borderRadius: '6px',
    borderRadiusSmall: '4px',

    fontFamily:
      'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif',
    fontFamilyMono:
      'ui-monospace, SFMono-Regular, "SF Mono", Consolas, "Liberation Mono", monospace',
    fontSize: '14px',
    fontSizeSmall: '13px',
    fontSizeLarge: '15px',
  },
  Button: {
    // 圆角统一 6px
    borderRadiusMedium: '6px',
    borderRadiusLarge: '6px',
    borderRadiusSmall: '6px',
    // 默认按钮文字 14px，small 13px
    fontWeight: '500',
  },
  Card: {
    borderRadius: '10px',
    borderColor: '#E8EAED',
    color: '#FFFFFF',
    paddingMedium: '20px 20px',
  },
  Input: {
    borderRadius: '6px',
    borderHover: '1px solid #18A058',
    borderFocus: '1px solid #18A058',
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '6px',
      },
    },
  },
  DataTable: {
    borderRadius: '0',
    borderColor: '#EEF0F2',
    thColor: '#F8FAFC',
    thColorHover: '#F8FAFC',
    thTextColor: '#667085',
    tdColorHover: '#F7F9FB',
    thFontWeight: '500',
    fontSizeMedium: '14px',
    fontSizeSmall: '13px',
    thPaddingSmall: '8px 12px',
    tdPaddingSmall: '8px 12px',
    thPaddingMedium: '12px 16px',
    tdPaddingMedium: '14px 16px',
  },
  Drawer: {
    borderRadius: '12px',
    headerBorderBottom: '1px solid #E8EAED',
    footerBorderTop: '1px solid #E8EAED',
    bodyPadding: '0 4px',
  },
  Tag: {
    borderRadius: '6px',
    fontSize: '12px',
  },
  Menu: {
    itemHeight: '40px',
    borderRadius: '8px',
    itemColorActive: 'rgba(24, 160, 88, 0.10)',
    itemColorActiveHover: 'rgba(24, 160, 88, 0.10)',
    itemColorActiveCollapsed: 'rgba(24, 160, 88, 0.10)',
    itemTextColorActive: '#0F8A4C',
    itemTextColorActiveHover: '#0F8A4C',
    itemIconColorActive: '#0F8A4C',
    itemIconColorActiveHover: '#0F8A4C',
    arrowColorActive: '#0F8A4C',
    itemTextColor: '#1F2937',
    itemTextColorHover: '#1F2937',
    itemIconColor: '#667085',
    itemColorHover: 'rgba(31, 41, 55, 0.04)',
  },
  Tabs: {
    tabFontWeightActive: '600',
    tabTextColorActive: '#0F8A4C',
    tabTextColorHover: '#0F8A4C',
    barColor: '#18A058',
  },
  Pagination: {
    itemBorderRadius: '6px',
  },
  Modal: {
    borderRadius: '12px',
  },
  Form: {
    labelTextColor: '#1F2937',
    labelFontWeight: '500',
  },
  Empty: {
    textColor: '#98A2B3',
  },
}
</script>

<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <router-view />
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
