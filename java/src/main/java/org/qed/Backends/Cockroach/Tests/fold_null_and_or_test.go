package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullAndOr(t *testing.T) {
	rule := loadRule(t, "FoldNullAndOr")
	e := newEnv(t)
	src, _ := e.scan("t", 3)
	nullBool := func() opt.ScalarExpr { return e.f.ConstructNull(types.Bool) }
	before := e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(nullBool(), nullBool())))
	e.expectRewrite(rule, before, e.f.ConstructSelect(src, e.filters(nullBool())))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructOr(nullBool(), nullBool()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructAnd(nullBool(), e.f.ConstructTrue()))))
}
