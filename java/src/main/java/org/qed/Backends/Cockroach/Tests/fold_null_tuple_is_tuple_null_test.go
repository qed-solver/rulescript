package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func nullIsNull(e *env) opt.ScalarExpr {
	return e.f.ConstructIs(e.f.ConstructNull(types.Int), memo.NullSingleton)
}

func TestFoldNullTupleIsTupleNull(t *testing.T) {
	rule := loadRule(t, "FoldNullTupleIsTupleNull")
	e := newEnv(t)
	src, _ := e.scan("t", 1)
	before := e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(nullIsNull(e), nullIsNull(e))))
	want := src
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructOr(nullIsNull(e), nullIsNull(e)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(nullIsNull(e), e.f.ConstructIs(e.num(1), memo.NullSingleton)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(nullIsNull(e))))
}
