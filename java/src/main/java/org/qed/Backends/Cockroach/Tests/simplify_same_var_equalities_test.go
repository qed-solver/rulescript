package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifySameVarEqualities(t *testing.T) {
	rule := loadRule(t, "SimplifySameVarEqualities")

	t.Run("single_column", func(t *testing.T) {
		e := newEnv(t)
		scan, cols := e.scan("t", 1)
		x := e.v(cols[0])
		before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(x, x)))
		want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructOr(
			e.f.ConstructIsNot(x, memo.NullSingleton),
			e.f.ConstructNull(x.DataType()),
		)))
		e.expectRewrite(rule, before, want)
	})

	t.Run("other_relation", func(t *testing.T) {
		e := newEnv(t)
		scan, cols := e.scan("u", 1)
		x := e.v(cols[0])
		before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(x, x)))
		want := e.f.ConstructSelect(scan, e.filters(e.f.ConstructOr(
			e.f.ConstructIsNot(x, memo.NullSingleton),
			e.f.ConstructNull(x.DataType()),
		)))
		e.expectRewrite(rule, before, want)
	})

	t.Run("distinct_columns", func(t *testing.T) {
		e := newEnv(t)
		scan, cols := e.scan("t", 2)
		before := e.f.ConstructSelect(scan, e.filters(e.f.ConstructEq(e.v(cols[0]), e.v(cols[1]))))
		e.expectNoMatch(rule, before)
	})
}
