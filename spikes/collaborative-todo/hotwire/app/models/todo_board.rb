class TodoBoard
  Entry = Data.define(:id, :title, :completed)
  FILTERS = %w[all active completed].freeze

  def initialize
    @lock = Mutex.new
    @entries = {}
    @next_id = 0
  end

  def snapshot(filter)
    @lock.synchronize do
      entries = @entries.values
      visible = filter == "all" ? entries : entries.select { |item| item.completed == (filter == "completed") }
      [visible, entries.count { |item| !item.completed }, entries.size]
    end
  end

  def add(title)
    @lock.synchronize do
      @next_id += 1
      @entries[@next_id] = Entry.new(id: @next_id, title: title, completed: false)
    end
  end

  def toggle(id)
    @lock.synchronize do
      item = @entries[id]
      return false unless item

      @entries[id] = item.with(completed: !item.completed)
      true
    end
  end

  def delete(id)
    @lock.synchronize { !!@entries.delete(id) }
  end
end
