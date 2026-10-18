package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveZeroCardLock(t *testing.T) {
	rule := loadRule(t, "RemoveZeroCardLock")
	t.Run("zero_row_input", func(t *testing.T) {
		e := newEnv(t)
		_, cols := e.scan("u", 1)
		zero := e.empty(cols[0])
		lock := e.f.ConstructFunction(memo.ScalarListExpr{e.v(cols[0])}, &memo.FunctionPrivate{Name: "lock", Typ: types.Bool, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{Class: tree.NormalClass}})
		before := e.f.ConstructSelect(zero, e.filters(lock))
		want := e.empty(cols[0])
		e.expectRewrite(rule, before, want)
	})
	t.Run("nonempty_input", func(t *testing.T) {
		e := newEnv(t)
		_, cols := e.scan("u", 1)
		input := e.intValues(cols, []int{1})
		lock := e.f.ConstructFunction(memo.ScalarListExpr{e.v(cols[0])}, &memo.FunctionPrivate{Name: "lock", Typ: types.Bool, Properties: &tree.FunctionProperties{}, Overload: &tree.Overload{Class: tree.NormalClass}})
		plan := e.f.ConstructSelect(input, e.filters(lock))
		e.expectNoMatch(rule, plan)
	})
}
