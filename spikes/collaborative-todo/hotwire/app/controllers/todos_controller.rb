class TodosController < ActionController::Base
  protect_from_forgery with: :exception
  rescue_from ActionController::InvalidAuthenticityToken, with: :forbidden

  BOARD = TodoBoard.new

  before_action :load_filter

  def index
    render_board
  end

  def create
    @draft = params[:title].to_s
    title = @draft.strip
    return render_board(status: :bad_request) unless title.length.between?(1, 120)

    BOARD.add(title)
    broadcast_change
  end

  def toggle
    return head :not_found unless BOARD.toggle(params[:id].to_i)

    broadcast_change
  end

  def delete
    return head :not_found unless BOARD.delete(params[:id].to_i)

    broadcast_change
  end

  private

  def load_filter
    @filter = params.fetch(:filter, "all")
    head :bad_request unless TodoBoard::FILTERS.include?(@filter)
  end

  def render_board(status: :ok)
    @todos, @remaining, @total = BOARD.snapshot(@filter)
    render :index, layout: "application", status: status
  end

  def broadcast_change
    Turbo::StreamsChannel.broadcast_refresh_to("collaboration-board", request_id: Turbo.current_request_id)
    redirect_to root_path(filter: @filter), status: :see_other
  end

  def forbidden
    head :forbidden
  end
end
