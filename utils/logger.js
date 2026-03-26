const SystemLog = require('../models/SystemLog');

const logger = {
    error: async (message, stack = '', path = '', metadata = {}) => {
        try {
            console.error(`🔴 [LOG ERROR]: ${message}`, path);
            await SystemLog.create({ level: 'error', message, stack, path, metadata });
        } catch (e) {
            console.error('Failed to save error log', e);
        }
    },
    warn: async (message, path = '', metadata = {}) => {
        try {
            console.warn(`🟡 [LOG WARN]: ${message}`, path);
            await SystemLog.create({ level: 'warn', message, path, metadata });
        } catch (e) {
            console.error('Failed to save warn log', e);
        }
    },
    info: async (message, path = '', metadata = {}) => {
        try {
            console.info(`🔵 [LOG INFO]: ${message}`, path);
            await SystemLog.create({ level: 'info', message, path, metadata });
        } catch (e) {
            console.error('Failed to save info log', e);
        }
    }
};

module.exports = logger;
