const path = require('path');
const { createWebpackConfig } = require('../../webpack/createWebpackConfig');

module.exports = createWebpackConfig({
  appFolderName: 'host-app',
  federationName: 'host_app',
  port: 3000,
  title: 'Host App',
  entry: path.resolve(__dirname, 'src/index.tsx'),
  remotes: {
    invoice_app: 'invoice_app@http://localhost:3001/remoteEntry.js',
    supply_chain_app: 'supply_chain_app@http://localhost:3002/remoteEntry.js',
    dashboard_app: 'dashboard_app@http://localhost:3003/remoteEntry.js',
  },
});
