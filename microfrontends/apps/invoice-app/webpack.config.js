const path = require('path');
const { createWebpackConfig } = require('../../webpack/createWebpackConfig');

module.exports = createWebpackConfig({
  appFolderName: 'invoice-app',
  federationName: 'invoice_app',
  port: 3001,
  title: 'Invoice App',
  entry: path.resolve(__dirname, 'src/index.tsx'),
  exposes: {
    './InvoiceModule': path.resolve(__dirname, 'src/InvoiceModule.tsx'),
  },
});
