const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const webpack = require('webpack');

const dependencies = require('../package.json').dependencies;

const repoRoot = path.resolve(__dirname, '../..');
const microRoot = path.resolve(__dirname, '..');

function defineImportMetaEnv(key, fallback) {
  return JSON.stringify(process.env[key] || fallback);
}

function createWebpackConfig({
  appFolderName,
  federationName,
  port,
  entry,
  title,
  exposes = {},
  remotes = {},
}) {
  return {
    mode: process.env.NODE_ENV === 'production' ? 'production' : 'development',
    devtool:
      process.env.NODE_ENV === 'production'
        ? 'source-map'
        : 'eval-cheap-module-source-map',
    entry,
    output: {
      path: path.resolve(microRoot, 'dist', appFolderName),
      publicPath: 'auto',
      clean: true,
    },
    resolve: {
      extensions: ['.tsx', '.ts', '.jsx', '.js', '.json'],
      alias: {
        '@frontend': path.resolve(repoRoot, 'frontend/src'),
        '@mfe-shared': path.resolve(microRoot, 'shared/src'),
        react: path.resolve(microRoot, 'node_modules/react'),
        'react/jsx-runtime': path.resolve(microRoot, 'node_modules/react/jsx-runtime.js'),
        'react/jsx-dev-runtime': path.resolve(
          microRoot,
          'node_modules/react/jsx-dev-runtime.js',
        ),
        'react-dom': path.resolve(microRoot, 'node_modules/react-dom'),
        'react-dom/client': path.resolve(microRoot, 'node_modules/react-dom/client.js'),
        'react-router-dom': path.resolve(microRoot, 'node_modules/react-router-dom'),
      },
    },
    module: {
      rules: [
        {
          test: /\.tsx?$/,
          loader: 'ts-loader',
          options: {
            configFile: path.resolve(microRoot, 'tsconfig.json'),
          },
          exclude: /node_modules/,
        },
        {
          test: /\.css$/i,
          use: ['style-loader', 'css-loader'],
        },
      ],
    },
    devServer: {
      port,
      hot: true,
      historyApiFallback: true,
      allowedHosts: 'all',
      headers: {
        'Access-Control-Allow-Origin': '*',
      },
      static: {
        directory: path.resolve(microRoot, 'dist', appFolderName),
      },
    },
    plugins: [
      new webpack.container.ModuleFederationPlugin({
        name: federationName,
        filename: 'remoteEntry.js',
        exposes,
        remotes,
        shared: {
          react: {
            singleton: true,
            requiredVersion: dependencies.react,
          },
          'react-dom': {
            singleton: true,
            requiredVersion: dependencies['react-dom'],
          },
          'react-router-dom': {
            singleton: true,
            requiredVersion: dependencies['react-router-dom'],
          },
        },
      }),
      new HtmlWebpackPlugin({
        title,
        inject: false,
        templateContent: ({ htmlWebpackPlugin }) => `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${htmlWebpackPlugin.options.title}</title>
  </head>
  <body>
    <div id="root"></div>
    <script defer src="/main.js"></script>
  </body>
</html>`,
      }),
      new webpack.DefinePlugin({
        'import.meta.env.VITE_API_BASE_URL': defineImportMetaEnv(
          'VITE_API_BASE_URL',
          'http://localhost:8080/api',
        ),
        'import.meta.env.VITE_AI_API_BASE_URL': defineImportMetaEnv(
          'VITE_AI_API_BASE_URL',
          'http://127.0.0.1:8001',
        ),
        'import.meta.env.VITE_ANALYTICS_API_BASE_URL': defineImportMetaEnv(
          'VITE_ANALYTICS_API_BASE_URL',
          'http://127.0.0.1:5001',
        ),
      }),
    ],
    experiments: {
      topLevelAwait: true,
    },
  };
}

module.exports = {
  createWebpackConfig,
};
