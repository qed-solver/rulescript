package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldIsNotNull(t *testing.T) {
	rule := loadRule(t, "FoldIsNotNull")
	e := newEnv(t)
	src, _ := e.scan("t", 1)
	nullInt := e.f.ConstructNull(types.Int)
	before := e.f.ConstructSelect(src, e.filters(e.f.ConstructIsNot(nullInt, memo.NullSingleton)))
	e.expectRewrite(rule, before, e.f.ConstructSelect(src, e.filters(e.f.ConstructFalse())))
	scan3, cols := e.scan("t", 3)
	e.expectNoMatch(rule, e.f.ConstructSelect(scan3, e.filters(e.f.ConstructIsNot(e.v(cols[1]), memo.NullSingleton))))
	e.expectNoMatch(rule, e.f.ConstructSelect(scan3, e.filters(e.f.ConstructIs(e.v(cols[1]), memo.NullSingleton))))
}
