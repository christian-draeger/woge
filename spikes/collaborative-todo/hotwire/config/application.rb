require "bundler/setup"
require "rails"
require "action_controller/railtie"
require "action_view/railtie"
require "action_cable/engine"
require "turbo-rails"

module CollaborationStudy
  class Application < Rails::Application
    config.load_defaults 8.1
    config.eager_load = true
    config.secret_key_base = ENV.fetch("STUDY_SECRET_KEY_BASE")
    config.hosts = ["127.0.0.1"]
    config.force_ssl = false
    config.public_file_server.enabled = true
    config.middleware.insert_before ActionDispatch::Static, ActionDispatch::Static,
                                    Turbo::Engine.root.join("app/assets/javascripts").to_s
    config.action_controller.forgery_protection_origin_check = true
    config.action_cable.worker_pool_size = 4
    config.logger = ActiveSupport::Logger.new($stdout)
    config.log_level = :warn
    config.session_store :cookie_store, key: "collaboration_study_rails"
  end
end

Rails.application.initialize!
