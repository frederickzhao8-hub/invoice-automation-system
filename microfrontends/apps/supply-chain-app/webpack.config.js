const path = require('path');
const { createWebpackConfig } = require('../../webpack/createWebpackConfig');

module.exports = createWebpackConfig({
  appFolderName: 'supply-chain-app',
  federationName: 'supply_chain_app',
  port: 3002,
  title: 'Supply Chain App',
  entry: path.resolve(__dirname, 'src/index.tsx'),
  exposes: {
    './SupplyChainModule': path.resolve(__dirname, 'src/SupplyChainModule.tsx'),
  },
});
