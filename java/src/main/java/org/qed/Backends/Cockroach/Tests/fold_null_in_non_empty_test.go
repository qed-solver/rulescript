package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullInNonEmpty(t *testing.T) {
	rule := loadRule(t, "FoldNullInNonEmpty")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	nullInt := e.f.ConstructNull(types.Int)
	nullBool := e.f.ConstructNull(types.Bool)
	first := e.f.ConstructEq(nullInt, e.v(cols[0]))
	second := e.f.ConstructEq(nullInt, e.v(cols[1]))
	e.expectRewrite(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(first, second))),
		e.f.ConstructSelect(s, e.filters(nullBool)))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructAnd(first, second))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructOr(first, e.f.ConstructEq(e.v(cols[1]), nullInt)))))
	mixedInt := e.f.Metadata().AddColumn("mixed_int", types.Int)
	mixedBool := e.f.Metadata().AddColumn("mixed_bool", types.Bool)
	mixed := e.empty(mixedInt, mixedBool)
	e.expectNoMatch(rule, e.f.ConstructSelect(mixed, e.filters(e.f.ConstructOr(
		e.f.ConstructEq(nullInt, e.v(mixedInt)),
		e.f.ConstructEq(nullBool, e.v(mixedBool))))))
}

func TestSQLFoldNullInNonEmpty(t *testing.T) {
	expectSQLFires(t, "FoldNullInNonEmpty", "SELECT * FROM (SELECT x, y FROM u) q WHERE NULL::INT = q.x OR NULL::INT = q.y")
}
