package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestUnionToValues(t *testing.T) {
	rule := loadRule(t, "UnionToValues")
	e := newEnv(t)
	md := e.f.Metadata()
	pair := func(name string) opt.ColList {
		return opt.ColList{md.AddColumn(name+"0", types.Int), md.AddColumn(name+"1", types.Int)}
	}
	a, b, c, ab, out := pair("a"), pair("b"), pair("c"), pair("ab"), pair("out")
	union := func(last int) memo.RelExpr {
		first := e.f.ConstructUnionAll(e.intValues(a, []int{1, 2}, []int{3, 4}), e.intValues(b, []int{3, 4}, []int{5, 6}),
			&memo.SetPrivate{LeftCols: a, RightCols: b, OutCols: ab})
		return e.f.ConstructUnionAll(first, e.intValues(c, []int{7, last}),
			&memo.SetPrivate{LeftCols: ab, RightCols: c, OutCols: out})
	}
	want := e.intValues(out, []int{1, 2}, []int{3, 4}, []int{3, 4}, []int{5, 6}, []int{7, 8})
	e.expectRewrite(rule, union(8), want)
	e.expectNoMatch(rule, union(9))
	e.expectNoMatch(rule, e.f.ConstructUnion(e.intValues(a, []int{1, 2}), e.intValues(b, []int{3, 4}),
		&memo.SetPrivate{LeftCols: a, RightCols: b, OutCols: ab}))
}

func TestSQLUnionToValues(t *testing.T) {
	expectSQLFires(t, "UnionToValues", "SELECT * FROM (VALUES (1, 2), (3, 4)) UNION ALL SELECT * FROM (VALUES (3, 4), (5, 6)) UNION ALL SELECT * FROM (VALUES (7, 8))")
}
