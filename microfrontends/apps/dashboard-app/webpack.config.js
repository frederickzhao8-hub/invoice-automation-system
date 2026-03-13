const path = require('path');
const { createWebpackConfig } = require('../../webpack/createWebpackConfig');

module.exports = createWebpackConfig({
  appFolderName: 'dashboard-app',
  federationName: 'dashboard_app',
  port: 3003,
  title: 'Dashboard App',
  entry: path.resolve(__dirname, 'src/index.tsx'),
  exposes: {
    './DashboardModule': path.resolve(__dirname, 'src/DashboardModule.tsx'),
  },
});
