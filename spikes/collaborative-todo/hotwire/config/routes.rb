Rails.application.routes.draw do
  root "todos#index"
  post "/woge-actions/add-todo", to: "todos#create"
  post "/woge-actions/toggle-todo", to: "todos#toggle"
  post "/woge-actions/delete-todo", to: "todos#delete"
  mount ActionCable.server => "/cable"
end
