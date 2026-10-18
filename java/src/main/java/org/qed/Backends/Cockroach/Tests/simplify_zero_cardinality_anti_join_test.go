package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyZeroCardinalityAntiJoin(t *testing.T) {
	rule := loadRule(t, "SimplifyZeroCardinalityAntiJoin")
	t.Run("one-column left", func(t *testing.T) {
		e := newEnv(t)
		left, cols := e.scan("t", 1)
		rightCol := e.f.Metadata().AddColumn("one", types.Int)
		right := e.intValues([]opt.ColumnID{rightCol}, []int{0})
		before := e.f.ConstructAntiJoin(left, right, nil, &memo.JoinPrivate{})
		want := e.empty(cols...)
		e.expectRewrite(rule, before, want)
	})
	t.Run("empty right", func(t *testing.T) {
		e := newEnv(t)
		left, _ := e.scan("t", 1)
		rightCol := e.f.Metadata().AddColumn("zero", types.Int)
		right := e.intValues([]opt.ColumnID{rightCol})
		before := e.f.ConstructAntiJoin(left, right, nil, &memo.JoinPrivate{})
		e.expectNoMatch(rule, before)
	})
}
