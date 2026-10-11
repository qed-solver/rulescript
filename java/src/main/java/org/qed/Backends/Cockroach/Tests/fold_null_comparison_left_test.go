package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullComparisonLeft(t *testing.T) {
	rule := loadRule(t, "FoldNullComparisonLeft")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	nullInt := e.f.ConstructNull(types.Int)
	nullBool := e.f.ConstructNull(types.Bool)
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(nullInt, e.v(cols[0])))),
		e.f.ConstructSelect(s, e.filters(nullBool)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.v(cols[0]), nullInt))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructEq(e.num(1), e.v(cols[0])))))
}
