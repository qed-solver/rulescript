package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyIsNullCondition(t *testing.T) {
	rule := loadRule(t, "SimplifyIsNullCondition")
	t.Run("not-null column with another conjunct", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("nn", 2)
		other := e.f.ConstructEq(e.v(cols[0]), e.num(7))
		isNull := e.f.ConstructIs(e.v(cols[0]), memo.NullSingleton)
		before := e.f.ConstructSelect(e.f.ConstructSelect(input, e.filters(other)), e.filters(isNull))
		want := e.f.ConstructSelect(input, e.filters(e.f.ConstructFalse()))
		e.expectRewrite(rule, before, want)
	})
	t.Run("nullable column remains unchanged", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("t", 2)
		isNull := e.f.ConstructIs(e.v(cols[1]), memo.NullSingleton)
		plan := e.f.ConstructSelect(input, e.filters(isNull))
		e.expectNoMatch(rule, plan)
	})
}

