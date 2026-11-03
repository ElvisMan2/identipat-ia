module.exports = function (config) {
  config.set({
    frameworks: ['jasmine'],
    browserNoActivityTimeout: 120000,
    customLaunchers: {
      ChromeHeadlessNoGpu: {
        base: 'ChromeHeadless',
        flags: ['--no-sandbox', '--disable-gpu']
      }
    }
  });
};
